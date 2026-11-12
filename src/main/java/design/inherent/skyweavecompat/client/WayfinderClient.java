package design.inherent.skyweavecompat.client;

import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
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
    private final KeyMapping key = new KeyMapping("key.skyweave_compat.wayfinder",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,
            "key.categories.skyweave_compat");
    private boolean held;
    private Job job;
    private final WayfinderFeedback feedback=new WayfinderFeedback();
    private int feedbackIdle;
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
            new WayfinderSettings(true,0,WayfinderSettings.DEFAULT.placementLookupHeight(),budget,1,200,query.blocked());
            LodColumns columns = OptionalIntegrations.enabled(Feature.WAYFINDER_DH) ? WayfinderDhClient.open() : null;
            trace = new WayfinderTrace(ray,Wayfinder.domain(ray,level),columns,query.blocked(),WayfinderFrames.open(level,ray));
        }
    }
    private WayfinderClient(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        NeoForge.EVENT_BUS.addListener(this::tick);
        WayfinderNetwork.client = this::receive;
        WayfinderNetwork.status = this::status;
    }
    public static void register(IEventBus bus) { new WayfinderClient(bus); }
    private void receive(WayfinderPacket packet) {
        var client = Minecraft.getInstance();
        if(packet.operation()==WayfinderPacket.BEGIN) {
            if(client.level!=null && client.player!=null && packet.dimension().equals(client.level.dimension().location().toString())
                    && feedback.begin(packet.request(),client.level,client.getConnection(),packet.dimension()))feedbackIdle=0;
            return;
        }
        if (packet.operation() == WayfinderPacket.CANCEL) {
            if (job != null && job.id.equals(packet.request())) { job.trace.close(); job = null; }
            if(client.level!=null)feedback.retire(packet.request(),client.level,client.getConnection(),
                    client.level.dimension().location().toString());
            return;
        }
        if (packet.operation() != WayfinderPacket.QUERY) return;
        if (client.player == null || client.level == null || client.screen != null) {
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.CANCEL,packet.request())); return;
        }
        if(!feedback.matches(packet.request(),client.level,client.getConnection(),packet.dimension()))return;
        if (job != null) cancel(true);
        try { job = new Job(packet,client); }
        catch (RuntimeException invalid) {
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.FAILED,packet.request()));
            com.mojang.logging.LogUtils.getLogger().warn("Cannot start Wayfinder query",invalid);
        }
    }
    private void status(WayfinderStatusPacket packet) {
        var client=Minecraft.getInstance();
        if(client.level==null || client.player==null || !feedback.accept(packet,client.level,client.getConnection(),
                client.level.dimension().location().toString()))return;
        feedbackIdle=0;
        if(WayfinderPreferences.DEBUG_MESSAGES.get() && client.player.hasPermissions(2))
            client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "skyweave_compat.wayfinder.status",design.inherent.skyweavecompat.terrain.LocatorText.text("wayfinder",packet.status().key()),
                    net.minecraft.network.chat.Component.translatable("skyweave_compat.wayfinder.stage."+
                            packet.stage().name().toLowerCase(java.util.Locale.ROOT))),true);
    }
    private void cancel(boolean notify) {
        if(job==null) {
            if(notify && feedback.active())PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.CANCEL,feedback.request()));
            return;
        }
        var old = job; job = null; old.trace.close();
        if (notify && Minecraft.getInstance().getConnection() == old.connection)
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.CANCEL,old.id));
    }
    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if(!feedback.sameSession(client.level,client.getConnection())) {cancel(false);feedback.clear();}
        if(feedback.active() && ++feedbackIdle>1200) {cancel(true);feedback.clear();}
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        // A quick tap can press and release between ticks; held-key repeats still do not retrigger.
        boolean down = key.isDown(), pressed = !held && (down || clicked); held = down;
        if (pressed && client.player != null && client.level != null && client.screen == null) {
            if (job != null || feedback.active()) cancel(true);
            else if (client.getConnection() != null && client.getConnection().hasChannel(WayfinderPacket.TYPE)) {
                feedback.clear();var id=UUID.randomUUID();
                feedback.begin(id,client.level,client.getConnection(),client.level.dimension().location().toString());feedbackIdle=0;
                PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.START,id));
            } else if(WayfinderPreferences.DEBUG_MESSAGES.get() && client.player.hasPermissions(2))
                client.player.displayClientMessage(design.inherent.skyweavecompat.terrain.LocatorText.text("wayfinder","unsupported_peer"),true);
        }
        if (job == null) return;
        if (client.player == null || client.level != job.level || client.getConnection() != job.connection || client.screen != null) {
            cancel(true); return;
        }
        var eye = client.player.getEyePosition(); var view = client.player.getViewVector(1);
        if (eye.distanceToSqr(new Vec3(job.ray.x(),job.ray.y(),job.ray.z())) > .25
                || !job.ray.sameDirection(view.x,view.y,view.z)) { cancel(true); return; }
        if (++job.ticks % 40 == 0) {
            PacketDistributor.sendToServer(WayfinderPacket.signal(WayfinderPacket.ALIVE,job.id));
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
            if(result.region()!=null) {
                PacketDistributor.sendToServer(new WayfinderCandidatePacket(done.id,result.region()));return;
            }
            var p = result.hit();
            PacketDistributor.sendToServer(new WayfinderPacket(WayfinderPacket.HIT,done.id,p.x(),p.y(),p.z(),0,0,0,"",0,0,List.of()));
        } else PacketDistributor.sendToServer(WayfinderPacket.signal(switch (result.outcome()) {
            case MISSING -> WayfinderPacket.MISSING;
            case MISS -> WayfinderPacket.MISS;
            case CANCELLED -> WayfinderPacket.CANCEL;
            case CONSTRUCT -> WayfinderPacket.CONSTRUCT;
            case INCOMPLETE -> WayfinderPacket.INCOMPLETE;
            default -> WayfinderPacket.FAILED;
        },done.id));
    }
}
