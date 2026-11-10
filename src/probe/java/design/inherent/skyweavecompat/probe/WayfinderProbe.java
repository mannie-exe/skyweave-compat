package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderPacket;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.registration.*;

/** Actual command/tick/shape/teleport behavior in a disposable native world. */
final class WayfinderProbe {
    private static ServerPlayer player;
    private static Path output;
    private static final JsonObject report = new JsonObject();
    private static final List<String> feedback = new ArrayList<>();
    private static boolean active;
    private static boolean highChecked;
    private static boolean cancelTeleport, teleportHookSeen;
    private static int step, ticks, changed;
    private static BlockPos target;
    static void start(MinecraftServer server, Path destination) throws Exception {
        output = destination;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand event) -> {
                    if (cancelTeleport && event.getEntity() == player) {
                        teleportHookSeen = true; event.setCanceled(true);
                    }
                });
        report.addProperty("nativeMinY",server.overworld().getMinBuildHeight());
        report.addProperty("nativeMaxY",server.overworld().getMaxBuildHeight());
        if (Boolean.getBoolean("neighbors.probe.wayfinderHigh")
                && (server.overworld().getMinBuildHeight() != -128 || server.overworld().getMaxBuildHeight() !=768))
            throw new AssertionError("Custom-height fixture did not load the actual Skyweave bounds");
        var cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WayfinderProbe"),0,
                net.minecraft.server.level.ClientInformation.createDefault(),false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        player = ProbePlayers.login(server,cookie,new ServerPlayer(server,server.overworld(),cookie.gameProfile(),cookie.clientInformation()) {
            @Override public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) {
                if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)
                    feedback.add(text.getKey());
                super.displayClientMessage(message,actionBar);
            }
        });
        server.getPlayerList().op(player.getGameProfile()); player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true); player.getAbilities().flying = true;
        // Preserve all other mock channels but explicitly qualify the command-only native path.
        var connection = player.connection.getConnection();
        var setup = ChannelAttributes.getPayloadSetup(connection);
        var channels = new HashMap<net.minecraft.network.ConnectionProtocol,Map<net.minecraft.resources.ResourceLocation,NetworkChannel>>();
        setup.channels().forEach((protocol,entries) -> {
            var copy = new HashMap<>(entries); copy.remove(WayfinderPacket.TYPE.id()); channels.put(protocol,copy);
        });
        ChannelAttributes.setPayloadSetup(connection,new NetworkPayloadSetup(channels));
        check(!player.connection.hasChannel(WayfinderPacket.TYPE),"Fixture retained client capability");
        for (int x = 0; x <= 5; x++) server.overworld().getChunk(x,0);
        target = new BlockPos(40,102,0); server.overworld().setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());
        geometry(server);
        reset(server,0);
        player.setGameMode(GameType.SPECTATOR); command(server); response("unavailable");
        player.setGameMode(GameType.CREATIVE);
        var boat = new net.minecraft.world.entity.vehicle.Boat(server.overworld(),.5,101,.5);
        check(player.startRiding(boat,true),"Native mounting fixture failed");
        command(server); response("unavailable"); player.stopRiding(); boat.discard();
        player.startFallFlying(); command(server); response("unavailable"); player.stopFallFlying();
        player.getAbilities().flying=true; player.onUpdateAbilities();
        report.addProperty("nativeMountedGlidingSpectatorRejected",true);
        // Riding changes the native passenger position; restore the sight-ray witness.
        reset(server,0);
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) { WayfinderSableProbe.start(server); step = -1; }
        command(server); active = true;
    }
    private static void geometry(MinecraftServer server) {
        var level = server.overworld();
        var states = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
        states.add(Blocks.OAK_LEAVES.defaultBlockState());
        states.add(Blocks.OAK_STAIRS.defaultBlockState().setValue(
                net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.EAST));
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                net.minecraft.resources.ResourceLocation.parse("create:andesite_casing")).ifPresent(block -> states.add(block.defaultBlockState()));
        var landings = new JsonArray();
        var position = new BlockPos(-40,110,0); level.getChunk(position.getX()>>4,0);
        for (var state : states) {
            level.setBlockAndUpdate(position,state);
            var shape = state.getCollisionShape(level,position,net.minecraft.world.phys.shapes.CollisionContext.of(player));
            var hit = shape.clip(new net.minecraft.world.phys.Vec3(-42,110.75,.5),
                    new net.minecraft.world.phys.Vec3(-38,110.75,.5),position);
            check(hit != null,"Missing native shape hit: "+state);
            var landing = design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,
                    new net.minecraft.world.phys.Vec3(-39.25,110.75,.5),
                    design.inherent.skyweavecompat.wayfinder.WayfinderSettings.DEFAULT.blockedBlocks());
            check(landing != null && landing.y == 111,"Invalid negative-coordinate shape landing: "+state);
            landings.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }
        level.setBlockAndUpdate(position,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(position.above(),Blocks.COBWEB.defaultBlockState());
        check(design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,
                new net.minecraft.world.phys.Vec3(-39.5,111,.5),
                design.inherent.skyweavecompat.wayfinder.WayfinderSettings.DEFAULT.blockedBlocks()) == null,
                "Non-colliding body hazard accepted");
        level.setBlockAndUpdate(position.above(),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(position,Blocks.AIR.defaultBlockState());
        check(!design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.blocked(Blocks.AIR.defaultBlockState(),List.of("absent_mod:missing")),
                "Missing optional block resolved to air");
        check(design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.blocked(Blocks.OAK_LOG.defaultBlockState(),List.of("#minecraft:logs")),
                "Block tag blacklist not applied");
        check(!design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.blocked(Blocks.MAGMA_BLOCK.defaultBlockState(),List.of()),
                "Blacklist removal retained hard-coded membership");
        var scale = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        double originalScale = scale.getBaseValue();
        level.setBlockAndUpdate(position,Blocks.STONE.defaultBlockState());
        try {
            for (double value : List.of(.0625,1.66665,2.,16.)) {
                scale.setBaseValue(value); player.refreshDimensions();
                var footprint = design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.footprint(player,position);
                check(footprint != null && footprint.chunks() <=4,"Scaled player lost bounded footprint: "+value);
                var landing = design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,
                        new net.minecraft.world.phys.Vec3(-39.5,111,.5),
                        design.inherent.skyweavecompat.wayfinder.WayfinderSettings.DEFAULT.blockedBlocks());
                check(landing != null && landing.y ==111,"Native scaled-player landing rejected: "+value);
            }
            report.addProperty("nativeScaleBounds",true);
        } finally {
            scale.setBaseValue(originalScale); player.refreshDimensions();
            level.setBlockAndUpdate(position,Blocks.AIR.defaultBlockState());
        }
        var far = new BlockPos(30000,102,0); level.getChunk(far.getX()>>4,0);
        level.setBlockAndUpdate(far,Blocks.STONE.defaultBlockState());
        var shallow = new design.inherent.skyweavecompat.wayfinder.SightRay(.5,102.5,.5,1,0,0);
        var surface = design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.surface(level,player,shallow,far,
                Wayfinder.domain(shallow,level).exit());
        check(surface != null && !surface.isInside() && Math.abs(surface.getLocation().x-30000)<1e-5,
                "Long ray used VoxelShape's inside sample as an actual hit");
        var inside = new design.inherent.skyweavecompat.wayfinder.SightRay(30000.5,102.5,.5,1,0,0);
        check(design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.surface(level,player,inside,far,
                Wayfinder.domain(inside,level).exit()) == null,"Origin inside a target was accepted");
        level.setBlockAndUpdate(far,Blocks.AIR.defaultBlockState());
        report.addProperty("nativeSurfacePrecisionAndInsideRejection",true);
        var observed = new net.minecraft.world.phys.Vec3(position.getX()+.5,position.getY()+.5,.5);
        for (int up=0;up<=8;up++) level.setBlockAndUpdate(position.above(up),Blocks.STONE.defaultBlockState());
        var exclusions = design.inherent.skyweavecompat.wayfinder.WayfinderSettings.DEFAULT.blockedBlocks();
        check(design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,observed,exclusions,7)==null,
                "Placement exceeded its configured upward range");
        var raised = design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,observed,exclusions,8);
        check(raised != null && raised.y==position.getY()+9,"Eight-block upward lookup did not reach safe support");
        level.setBlockAndUpdate(position.above(4),Blocks.MAGMA_BLOCK.defaultBlockState());
        check(design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,observed,exclusions,8)==null,
                "Upward lookup skipped a blacklisted support");
        for (int up=0;up<=8;up++) level.setBlockAndUpdate(position.above(up),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(position.below(),Blocks.STONE.defaultBlockState());
        check(design.inherent.skyweavecompat.wayfinder.WayfinderGeometry.landing(level,player,position,observed,exclusions,8)==null,
                "Placement fell back to a lower surface");
        level.setBlockAndUpdate(position.below(),Blocks.AIR.defaultBlockState());
        report.addProperty("boundedUpwardLookupAndHazardPolicy",true);
        report.add("nativeShapeLandings",landings); report.addProperty("bodyHazardAndBlacklistPolicy",true);
    }
    private static void reset(MinecraftServer server, float pitch) {
        player.teleportTo(server.overworld(),.5,101,.5,Set.of(),-90,pitch);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.fallDistance = 0;
    }
    private static void command(MinecraftServer server) throws Exception {
        feedback.clear();
        server.getCommands().getDispatcher().execute("skyweavecompat wayfinder",player.createCommandSourceStack());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static design.inherent.skyweavecompat.wayfinder.WayfinderSettings settings(MinecraftServer server) throws Exception {
        return (design.inherent.skyweavecompat.wayfinder.WayfinderSettings)field(state(server),"settings");
    }
    private static void response(String key) throws Exception {
        if (settings(player.server).debugMessages())
            check(feedback.contains("skyweave_compat.wayfinder."+key),"Missing "+key+" response: "+feedback);
        else check(feedback.isEmpty(),"Quiet Wayfinder emitted feedback: "+feedback);
    }
    private static boolean settled(MinecraftServer server) throws Exception {
        return ((Map<?,?>)field(state(server),"jobs")).get(player.getUUID()) == null;
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            if (++ticks > 1200) throw new AssertionError("Wayfinder fixture exceeded1200ticks");
            if (step == -1 && ticks > 3 && settled(server)) {
                response("construct"); check(player.getX() < 1,"Unsupported construct moved player");
                WayfinderSableProbe.verify(server);
                ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                report.addProperty("nativeConstructAndStorageFramesRejected",true);
                WayfinderSableProbe.moveBehindTarget(server); command(server); step = 0;
            } else if (step == 0 && player.getX() > 39) {
                check(Math.abs(player.getY()-103) < 1e-5,"Full-block landing height is incorrect");
                check(player.getYRot() == -90 && player.getXRot() == 0,"View direction changed");
                report.addProperty("nativeFullBlockLanding",true);
                if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
                    report.addProperty("ordinaryTargetBeforeConstructPreserved",true); WayfinderSableProbe.close(server);
                }
                response("success");
                if (settings(server).cooldownTicks() == 0) {
                    reset(server,0); command(server); request(server); step = 21; changed = ticks;
                } else {
                    command(server); response("cooldown"); check(player.getX() > 39,"Cooldown moved player");
                    report.addProperty("configuredCooldownEnforced",true); step = 1; changed = ticks;
                }
            } else if (step == 21 && player.getX() > 39) {
                response("success"); check(ticks-changed < 40,"Immediate-repeat fixture waited out the old cooldown");
                check(((Map<?,?>)field(state(server),"warps")).isEmpty(),"Disabled cooldown retained warp bookkeeping");
                report.addProperty("noCooldownImmediateRepeat",true);
                report.addProperty("defaultMessagesSilent",!settings(server).debugMessages());
                for (int up=1;up<=settings(server).placementLookupHeight();up++)
                    server.overworld().setBlockAndUpdate(target.above(up),Blocks.STONE.defaultBlockState());
                reset(server,0); command(server); step = 22; changed = ticks;
            } else if (step == 22 && player.getX() > 39) {
                response("success");
                check(player.getY()==103+settings(server).placementLookupHeight(),"Native upward placement missed the configured height");
                report.addProperty("nativeUpwardLookupHeight",settings(server).placementLookupHeight());
                for (int up=1;up<=settings(server).placementLookupHeight();up++)
                    server.overworld().setBlockAndUpdate(target.above(up),Blocks.AIR.defaultBlockState());
                step = 1; changed = ticks;
            } else if (step == 1 && ticks - changed > 42) {
                if (!highChecked && server.overworld().getMaxBuildHeight() > 640) {
                    highChecked = true;
                    server.overworld().setBlockAndUpdate(new BlockPos(40,601,0),Blocks.STONE.defaultBlockState());
                    player.teleportTo(server.overworld(),.5,600,.5,Set.of(),-90,0); command(server); step = 14; return;
                }
                server.overworld().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
                target = new BlockPos(80,102,0); server.overworld().setBlockAndUpdate(target,Blocks.OAK_SLAB.defaultBlockState());
                reset(server,.35f); command(server); step = 2;
            } else if (step == 14 && player.getX() > 39) {
                response("success"); check(Math.abs(player.getY()-602)<1e-5,"Native high-envelope warp failed");
                report.addProperty("nativeHighEnvelopeArrival",true);
                server.overworld().setBlockAndUpdate(new BlockPos(40,601,0),Blocks.AIR.defaultBlockState());
                step = 1; changed = ticks;
            } else if (step == 2 && player.getX() > 79) {
                check(Math.abs(player.getY()-102.5) < 1e-5,"Partial-block landing ignored its collision shape");
                response("success");
                report.addProperty("nativeSlabLanding",true); step = 3; changed = ticks;
            } else if (step == 3 && ticks - changed > 42) {
                server.overworld().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
                target = new BlockPos(40,102,0); server.overworld().setBlockAndUpdate(target,Blocks.CACTUS.defaultBlockState());
                reset(server,0); command(server); step = 4; changed = ticks;
            } else if (step == 4 && ticks - changed > 10 && settled(server)) {
                check(player.getX() < 1,"Hazard moved player"); response("unsafe");
                check(server.overworld().getBlockState(target).is(Blocks.CACTUS),"Hazard fixture disappeared");
                report.addProperty("nativeHazardRejected",true);
                server.overworld().setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());
                for (int up=1;up<=settings(server).placementLookupHeight()+1;up++)
                    server.overworld().setBlockAndUpdate(target.above(up),Blocks.STONE.defaultBlockState());
                command(server); step = 5; changed = ticks;
            } else if (step == 5 && ticks - changed > 10 && settled(server)) {
                check(player.getX() < 1,"Blocked headroom moved player"); response("unsafe"); report.addProperty("nativeHeadroomRejected",true);
                for (int up=1;up<=settings(server).placementLookupHeight()+1;up++)
                    server.overworld().setBlockAndUpdate(target.above(up),Blocks.AIR.defaultBlockState());
                server.overworld().setBlockAndUpdate(new BlockPos(0,104,0),Blocks.STONE.defaultBlockState());
                reset(server,-90); command(server); step = 13; changed = ticks;
            } else if (step == 13 && ticks - changed > 10 && settled(server)) {
                response("unsafe"); check(Math.abs(player.getY()-101)<1e-5,"Wayfinder ascended through a ceiling");
                report.addProperty("ceilingAscentRejected",true);
                server.overworld().setBlockAndUpdate(new BlockPos(0,104,0),Blocks.AIR.defaultBlockState());
                reset(server,0);
                cancelTeleport = true; command(server); step = 20; changed = ticks;
            } else if (step == 20 && ticks - changed > 10 && settled(server)) {
                response("denied"); check(teleportHookSeen && player.getX()<1,"Native teleport veto was bypassed");
                cancelTeleport = false; report.addProperty("nativeTeleportHookVetoPreserved",true);
                server.getPlayerList().deop(player.getGameProfile()); command(server);
                step = Boolean.getBoolean("neighbors.probe.wayfinderPublic") ? 15 : 6; changed = ticks;
            } else if (step == 15 && player.getX() > 39) {
                response("success"); check(!player.hasPermissions(2),"Public-access witness was still an operator");
                var settings = (design.inherent.skyweavecompat.wayfinder.WayfinderSettings)field(state(server),"settings");
                check(!settings.operatorsOnly(),"Server policy did not apply");
                report.addProperty("explicitServerPolicyAllowsNonOperator",true);
                ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                report.addProperty("complete",true); save(); active=false; server.halt(false);
            } else if (step == 6 && ticks - changed > 10) {
                check(player.getX() < 1,"Non-operator moved player"); response("denied"); report.addProperty("nonOperatorRejected",true);
                player.addTag("wayfinder.allowed"); command(server); response("denied");
                report.addProperty("legacyTagGrantsNoPermission",true);
                feedback.clear();
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.START,new UUID(0,0)));
                response("denied"); check(requestOrNull(server) == null,"Denied hotkey acquired work");
                report.addProperty("nonOperatorHotkeyFollowsDebugPolicy",true);
                server.getPlayerList().op(player.getGameProfile());
                // Embedded endpoints qualify server validation, not remote transport.
                net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(player.connection.getConnection());
                check(player.connection.hasChannel(WayfinderPacket.TYPE),"Missing mock client capability");
                for (double[] point : List.of(new double[]{Double.NaN,102,.5}, new double[]{30000001,102,.5},
                        new double[]{40,102,30})) {
                    command(server); var id = request(server);
                    Wayfinder.receive(player,new WayfinderPacket(WayfinderPacket.HIT,id,point[0],point[1],point[2],0,0,0,"",0,0,List.of()));
                    response("invalid"); check(requestOrNull(server) == null,"Invalid request remained active");
                    ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                }
                report.addProperty("invalidCandidatesRejectedBeforeLoading",true);
                command(server); var id = request(server);
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.CANCEL,UUID.randomUUID()));
                check(request(server).equals(id),"Stale response cancelled current request");
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.CANCEL,id));
                response("cancelled");
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.START,new UUID(0,0)));
                var replacement = request(server);
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.CANCEL,id));
                check(request(server).equals(replacement),"Previous lifetime cancelled replacement");
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.CANCEL,replacement));
                report.addProperty("staleAndDuplicateResponsesIgnored",true);
                command(server); server.getPlayerList().deop(player.getGameProfile());
                step = 7; changed = ticks;
            } else if (step == 7 && ticks - changed > 2) {
                response("cancelled"); check(requestOrNull(server) == null,"Revoked operator retained a request");
                check(player.getX() < 1,"Revoked permission moved player");
                report.addProperty("pendingPermissionRevocation",true);
                server.getPlayerList().op(player.getGameProfile()); command(server);
                player.teleportTo(server.overworld(),2.5,101,.5,Set.of(),-90,0);
                step = 8; changed = ticks;
            } else if (step == 8 && ticks - changed > 2) {
                response("cancelled"); check(requestOrNull(server) == null,"Moved player retained request");
                report.addProperty("pendingMovementCancelled",true);
                reset(server,0); command(server);
                Wayfinder.receive(player,new WayfinderPacket(WayfinderPacket.HIT,request(server),100000.5,102.62,.5,0,0,0,"",0,0,List.of()));
                step = 9; changed = ticks;
            } else if (step == 9 && requestOrNull(server) == null) {
                response("obstructed"); report.addProperty("nearerNativeOcclusionRejected",true);
                server.overworld().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
                command(server);
                Wayfinder.receive(player,new WayfinderPacket(WayfinderPacket.HIT,request(server),100000.5,102.62,.5,0,0,0,"",0,0,List.of()));
                step = 10;
            } else if (step == 10) {
                var state = state(server);
                var job = ((Map<?,?>)field(state,"jobs")).get(player.getUUID());
                check(job != null,"Cold destination completed before lease observation: "+feedback);
                var leases = (Set<?>)field(job,"leases");
                if (leases.isEmpty()) return;
                check(leases.size() <= 4 && (int)field(state,"requests") <= 4,"Unbounded destination work");
                report.addProperty("observedColdDestinationLeases",leases.size());
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.CANCEL,(UUID)field(job,"id")));
                response("cancelled"); ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                check(player.getX() < 1,"Cancellation moved player provisionally");
                step = 11; changed = ticks;
            } else if (step == 11 && (int)field(state(server),"requests") == 0) {
                report.addProperty("pendingDestinationCancelledAndDrained",true);
                check(requestOrNull(server) == null,"Cancelled destination retained a job");
                command(server);
                // A plausible LOD candidate whose actual loaded cell is air must not create support.
                Wayfinder.receive(player,new WayfinderPacket(WayfinderPacket.HIT,request(server),100000.5,102.62,.5,0,0,0,"",0,0,List.of()));
                step = 19; changed = ticks;
            } else if (step == 19 && (int)field(state(server),"requests") == 0 && requestOrNull(server) == null) {
                response("unsafe"); check(player.getX()<1,"Contradictory destination moved the player");
                ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                report.addProperty("lodCandidateWithoutNativeSupportRejected",true);
                command(server);
                // Remove the actual native player while its authenticated request is active.
                server.getPlayerList().remove(player);
                step = 12; changed = ticks;
            } else if (step == 12 && ticks - changed > 2) {
                check(requestOrNull(server) == null,"Logged-out player retained request");
                report.addProperty("pendingDisconnectCancelled",true);
                var oldSession = player;
                var cookie = new CommonListenerCookie(oldSession.getGameProfile(),0,
                        net.minecraft.server.level.ClientInformation.createDefault(),false,
                        net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
                player = ProbePlayers.login(server,cookie); player.setGameMode(GameType.CREATIVE);
                Wayfinder.start(player); var newRequest = request(server);
                Wayfinder.receive(oldSession,WayfinderPacket.signal(WayfinderPacket.CANCEL,newRequest));
                check(newRequest.equals(request(server)),"Prior connection cancelled a new session's request");
                Wayfinder.start(oldSession);
                check(newRequest.equals(request(server)),"Prior connection replaced a new session's request");
                Wayfinder.receive(player,WayfinderPacket.signal(WayfinderPacket.CANCEL,newRequest));
                check(requestOrNull(server) == null,"Current session cancellation failed");
                report.addProperty("authenticatedSessionIdentityPreserved",true);
                Wayfinder.start(player); request(server);
                check(player.teleportTo(server.getLevel(net.minecraft.world.level.Level.END),.5,101,.5,Set.of(),-90,0),
                        "Native dimension travel failed");
                step = 16; changed = ticks;
            } else if (step == 16 && ticks - changed > 2) {
                check(requestOrNull(server) == null,"Dimension travel retained the old request");
                report.addProperty("dimensionChangeCancelled",true);
                player.teleportTo(server.overworld(),.5,101,.5,Set.of(),-90,0);
                concurrency(server);
                server.getPlayerList().remove(player);
                ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                report.addProperty("leasesReleased",true); report.addProperty("complete",true);
                report.addProperty("ticks",ticks); save(); active = false;
                if (Boolean.getBoolean("neighbors.probe.wayfinderDh")) {
                    report.addProperty("complete",false); save(); WayfinderDhProbe.start(server,output,report);
                } else { verifyShutdown(server); server.halt(false); }
            }
        } catch (Throwable error) {
            report.addProperty("complete",false); report.addProperty("error",error.toString()); error.printStackTrace();
            save(); active = false; server.halt(false);
        }
    }
    private static void concurrency(MinecraftServer server) throws Exception {
        var state = state(server);
        var settings = (design.inherent.skyweavecompat.wayfinder.WayfinderSettings)field(state,"settings");
        var actors = new ArrayList<ServerPlayer>();
        try {
            for (int index = 0; index <= settings.concurrentRequests(); index++) {
                var cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Wayfinder"+index),0,
                        net.minecraft.server.level.ClientInformation.createDefault(),false,
                        net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
                var actor = ProbePlayers.login(server,cookie); actors.add(actor);
                actor.setGameMode(GameType.CREATIVE); server.getPlayerList().op(actor.getGameProfile());
                actor.teleportTo(server.overworld(),.5,101,.5,Set.of(),-90,0);
                Wayfinder.start(actor);
            }
            var jobs = (Map<?,?>)field(state,"jobs");
            check(jobs.size() == settings.concurrentRequests() && !jobs.containsKey(actors.getLast().getUUID()),
                    "Concurrent admission exceeded its configured bound");
            check((int)field(state,"requests") == 0,"Pending client queries loaded chunks");
            report.addProperty("nativeConcurrentAdmissionBound",jobs.size());
        } finally {
            for (var actor : actors) server.getPlayerList().remove(actor);
        }
        check(((Map<?,?>)field(state,"jobs")).isEmpty(),"Concurrent logout retained jobs");
    }
    static void verifyShutdown(MinecraftServer server) throws Exception {
        var cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WayfinderStop"),0,
                net.minecraft.server.level.ClientInformation.createDefault(),false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var actor = ProbePlayers.login(server,cookie); actor.setGameMode(GameType.CREATIVE);
        server.getPlayerList().op(actor.getGameProfile()); Wayfinder.start(actor);
        var state = state(server);
        check(((Map<?,?>)field(state,"jobs")).containsKey(actor.getUUID()),"Shutdown witness had no pending query");
        report.addProperty("complete",false); save();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                    if (event.getServer() != server) return;
                    try {
                        check(((Map<?,?>)field(state,"jobs")).isEmpty(),"Shutdown retained pending jobs");
                        check(((java.util.concurrent.ThreadPoolExecutor)field(state,"worker")).isShutdown(),"Shutdown retained worker");
                        ProbeTickets.assertAbsent(server,Set.of("skyweave_wayfinder"));
                        report.addProperty("pendingShutdownCancelledAndWorkerStopped",true);
                        report.addProperty("complete",true); save();
                    } catch (Throwable failure) {
                        report.addProperty("error",failure.toString()); report.addProperty("complete",false); save();
                    }
                });
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static UUID requestOrNull(MinecraftServer server) throws Exception {
        var state = state(server);
        var job = ((Map<?,?>)field(state,"jobs")).get(player.getUUID());
        check((int)field(state,"requests") == 0,"Rejected/pending query acquired native chunk work");
        return job == null ? null : (UUID)field(job,"id");
    }
    private static Object state(MinecraftServer server) throws Exception {
        var registry = Wayfinder.class.getDeclaredField("SERVERS"); registry.setAccessible(true);
        return ((Map<?,?>)registry.get(null)).get(server);
    }
    private static UUID request(MinecraftServer server) throws Exception {
        var id = requestOrNull(server); check(id != null,"Expected a pending authenticated request"); return id;
    }
    private static void save() {
        try { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
