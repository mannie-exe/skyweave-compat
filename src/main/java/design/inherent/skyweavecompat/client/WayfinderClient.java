package design.inherent.skyweavecompat.client;

import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.terrain.LocatorText;
import design.inherent.skyweavecompat.wayfinder.*;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Deliberate world-only input and a single progressively evaluated server-authorized ray. */
public final class WayfinderClient {
    private static final UUID NONE = new UUID(0,0);
    private final KeyMapping key = new KeyMapping("key.skyweave_compat.wayfinder",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,
            "key.categories.skyweave_compat");
    private boolean held;
    private Job job;
    private static final class Job {
        final UUID id;
        final ClientLevel level;
        final ClientPacketListener connection;
        final SightRay ray;
        final WayfinderTrace trace;
        final int budget, timeout;
        int ticks, idle;
        Job(WayfinderPacket query, Minecraft client) {
            id = query.request(); level = client.level; connection = client.getConnection();
            ray = new SightRay(query.x(),query.y(),query.z(),query.dx(),query.dy(),query.dz());
            budget = query.steps(); timeout = query.waitTicks();
            if (budget < 16 || budget > 2048 || timeout < 20 || timeout > 1200
                    || !level.dimension().location().toString().equals(query.dimension()))
                throw new IllegalArgumentException("Invalid Wayfinder query");
            // Reuse the server-owned schema; blacklist membership never grants permission.
            new WayfinderSettings(true,true,40,budget,1,200,query.blocked());
            LodColumns columns = OptionalIntegrations.enabled(Feature.WAYFINDER_DH) ? WayfinderDhClient.open() : null;
            trace = new WayfinderTrace(ray,Wayfinder.domain(ray,level),columns,query.blocked(),WayfinderFrames.open(level,ray));
        }
    }
    private WayfinderClient(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        NeoForge.EVENT_BUS.addListener(this::tick);
        WayfinderNetwork.client = this::receive;
    }
    public static void register(IEventBus bus) { new WayfinderClient(bus); }
    private static void message(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(LocatorText.text("wayfinder",key),true);
    }
    private void receive(WayfinderPacket packet) {
        if (packet.operation() == WayfinderPacket.CANCEL) {
            if (job != null && job.id.equals(packet.request())) { job.trace.close(); job = null; }
            return;
        }
        if (packet.operation() != WayfinderPacket.QUERY) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) {
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.CANCEL,packet.request())); return;
        }
        if (job != null) cancel(true);
        try { job = new Job(packet,client); }
        catch (RuntimeException invalid) {
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.FAILED,packet.request()));
            com.mojang.logging.LogUtils.getLogger().warn("Cannot start Wayfinder query",invalid);
        }
    }
    private void cancel(boolean notify) {
        if (job == null) return;
        var old = job; job = null; old.trace.close();
        if (notify && Minecraft.getInstance().getConnection() == old.connection)
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.CANCEL,old.id));
    }
    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        // A quick tap can press and release between ticks; held-key repeats still do not retrigger.
        boolean down = key.isDown(), pressed = !held && (down || clicked); held = down;
        if (pressed && client.player != null && client.level != null && client.screen == null) {
            if (job != null) cancel(true);
            else if (client.getConnection() != null && client.getConnection().hasChannel(WayfinderPacket.TYPE))
                PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.START,NONE));
            else message("server_required");
        }
        if (job == null) return;
        if (client.player == null || client.level != job.level || client.getConnection() != job.connection || client.screen != null) {
            cancel(true); return;
        }
        var eye = client.player.getEyePosition(); var view = client.player.getViewVector(1);
        if (eye.distanceToSqr(new Vec3(job.ray.x(),job.ray.y(),job.ray.z())) > .25
                || !job.ray.sameDirection(view.x,view.y,view.z)) { cancel(true); return; }
        if (++job.ticks % 40 == 0) {
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.ALIVE,job.id)); message("pending");
        }
        long before = job.trace.workUnits();
        var result = job.trace.step(job.level,client.player,job.budget);
        if (result.outcome() == WayfinderTrace.Outcome.PENDING) {
            job.idle = before == job.trace.workUnits() ? job.idle + 1 : 0;
            if (job.idle < job.timeout) return;
            var expired = job; job = null; expired.trace.close();
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.INCOMPLETE,expired.id)); return;
        }
        var done = job; job = null;
        if (result.outcome() == WayfinderTrace.Outcome.HIT) {
            var p = result.hit();
            PacketDistributor.sendToServer(new WayfinderPacket(WayfinderPacket.HIT,done.id,p.x(),p.y(),p.z(),0,0,0,"",0,0,List.of()));
        } else PacketDistributor.sendToServer(WayfinderPacket.signal(switch (result.outcome()) {
            case MISSING -> WayfinderPacket.MISSING;
            case MISS -> WayfinderPacket.MISS;
            case CANCELLED -> WayfinderPacket.CANCEL;
            case CONSTRUCT -> WayfinderPacket.CONSTRUCT;
            default -> WayfinderPacket.FAILED;
        },done.id));
    }
}
