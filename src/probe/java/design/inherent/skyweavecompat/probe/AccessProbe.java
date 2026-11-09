package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.InitialSpawnState;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.portal.*;
import net.minecraft.world.phys.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** Calls native item, portal, entity and command boundaries in a disposable marked world. */
public final class AccessProbe {
    public static int synchronousStructureCalls;
    public static int synchronousBiomeCalls, synchronousPoiCalls;
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }

    static void run(MinecraftServer server, Path output) throws Exception {
        var report = new JsonObject();
        var level = server.overworld();
        var nether = server.getLevel(Level.NETHER);
        var spawn = level.getSharedSpawnPos();
        boolean enabled = !Boolean.getBoolean("neighbors.probe.policyDisabled");
        check(OptionalIntegrations.enabled(Feature.NETHER_ACCESS) == enabled, "Wrong access selection");
        check(OptionalIntegrations.enabled(Feature.STRUCTURE_COMMAND) == enabled, "Wrong command selection");
        if (enabled) {
            var field = ((GeologicalGenerator)level.getChunkSource().getGenerator()).skyweave$field(level.getChunkSource().randomState());
            var generator = (org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
            var host = field.sampleDominantIslandDescriptor(spawn.getX(), spawn.getZ(), generator.resolvedSkyIslandSettings());
            check(host != null && host.family() == org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.SATELLITE,
                    "Initial spawn is not on an accepted companion: " + spawn + ", selected="
                            + level.getDataStorage().computeIfAbsent(InitialSpawnState.FACTORY, InitialSpawnState.ID).selected());
            check(level.getBlockState(spawn.below()).isFaceSturdy(level, spawn.below(), Direction.UP), "Missing actual spawn support");
            check(level.getFluidState(spawn).isEmpty() && level.getFluidState(spawn.above()).isEmpty(), "Wet arrival");
            report.addProperty("initialCompanion", true);
        }
        report.addProperty("spawn", spawn.toShortString());
        var base = new BlockPos(spawn.getX(), 650, spawn.getZ());
        for (int x = -2; x <= 8; x++) for (int y = 0; y <= 5; y++)
            level.setBlockAndUpdate(base.offset(x, y, 0), y == 0 || x == 0 || x == 3 || y == 4
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
        // Only the intended 2x3 interior matters; side staging blocks are harmless support.
        var fire = base.offset(1, 1, 0);
        var shape = PortalShape.findEmptyPortalShape(level, fire, Direction.Axis.X).orElseThrow();
        // Use vanilla GameTestHelper's real player/embedded-connection pattern.
        // FakePlayer forbids mounting and is not registered in ChunkMap.
        var cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AccessProbe"), 0,
                net.minecraft.server.level.ClientInformation.createDefault(), false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var player = ProbePlayers.login(server, cookie);
        check(player.serverLevel() == level && level.getFluidState(player.blockPosition()).isEmpty(), "Unsafe native initial arrival");
        report.addProperty("nativeArrival", player.blockPosition().toShortString());
        for (var item : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
            var stack = new ItemStack(item, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(base.offset(1, 0, 0)), Direction.UP, base.offset(1, 0, 0), false));
            var result = item.useOn(context);
            if (enabled) {
                check(result == net.minecraft.world.InteractionResult.FAIL && stack.getCount() == 4 && stack.getDamageValue() == 0,
                        "Denied ignition consumed or damaged an item");
                check(level.getBlockState(fire).isAir(), "Denied item placed fire or portal");
            } else {
                check(level.getBlockState(fire).is(Blocks.NETHER_PORTAL), "Disabled policy changed native portal creation");
                for (int x = 1; x <= 2; x++) for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(base.offset(x,y,0), Blocks.AIR.defaultBlockState());
            }
        }
        shape.createPortalBlocks();
        check(level.getBlockState(fire).is(Blocks.NETHER_PORTAL) != enabled, "Shape creation policy differs");
        var zBase = base.offset(0, 0, 12);
        for (int z = 0; z <= 3; z++) for (int y = 0; y <= 4; y++)
            level.setBlockAndUpdate(zBase.offset(0, y, z), y == 0 || z == 0 || z == 3 || y == 4
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var zFire = zBase.offset(0, 1, 1);
        for (var item : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
            var stack = new ItemStack(item, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(zBase.offset(0, 0, 1)), Direction.UP, zBase.offset(0, 0, 1), false)));
            if (enabled) {
                check(result == net.minecraft.world.InteractionResult.FAIL && stack.getCount() == 4 && stack.getDamageValue() == 0,
                        "Denied Z-axis ignition consumed or damaged an item");
                check(level.getBlockState(zFire).isAir(), "Denied Z-axis ignition placed fire or portal");
            } else {
                check(level.getBlockState(zFire).is(Blocks.NETHER_PORTAL), "Disabled policy changed Z-axis creation");
                for (int z = 1; z <= 2; z++) for (int y = 1; y <= 3; y++)
                    level.setBlockAndUpdate(zBase.offset(0, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        PortalShape.findEmptyPortalShape(level, zFire, Direction.Axis.Z).orElseThrow().createPortalBlocks();
        check(level.getBlockState(zFire).is(Blocks.NETHER_PORTAL) != enabled, "Z-axis shape policy differs");
        report.addProperty("bothPortalAxes", true);
        if (enabled) {
            int before = nether.getChunkSource().getLoadedChunksCount();
            var portal = (NetherPortalBlock)Blocks.NETHER_PORTAL;
            check(portal.getPortalDestination(level, player, fire) == null, "Portal resolved an exit");
            var actor = new ArmorStand(EntityType.ARMOR_STAND, level);
            var transition = new DimensionTransition(nether, new Vec3(0, 70, 0), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING);
            check(actor.changeDimension(transition) == null && actor.level() == level && !actor.isRemoved(), "Entity transition mutated state");
            check(!actor.teleportTo(nether, 0,70,0, Set.of(),0,0), "Entity direct teleport accepted");
            check(player.changeDimension(transition) == null, "Player transition accepted");
            check(!player.teleportTo(nether,0,70,0,Set.of(),0,0), "Player boolean teleport accepted");
            actor.setPos(player.getX(), player.getY(), player.getZ());
            check(player.startRiding(actor, true), "Fixture player could not mount");
            player.teleportTo(nether,0,70,0,0,0);
            check(player.getVehicle() == actor && player.serverLevel() == level, "Denied void teleport dismounted player");
            player.stopRiding();
            player.setRespawnPosition(Level.NETHER, new BlockPos(0,70,0), 0, true, false);
            var recovery = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
            check(recovery.newLevel() == level, "Closed respawn dimension retained");
            check(nether.getChunkSource().getLoadedChunksCount() == before, "Denied travel loaded Nether chunks");
            report.addProperty("nativeEntryAndRespawnDenied", true);
            report.addProperty("netherChunkCountUnchanged", true);
        }
        // Native ordinary fire remains usable outside a frame.
        var ordinary = base.offset(7, 0, 0);
        level.setBlockAndUpdate(ordinary, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ordinary.above(), Blocks.AIR.defaultBlockState());
        var tool = new ItemStack(Items.FLINT_AND_STEEL); player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        check(Items.FLINT_AND_STEEL.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ordinary),Direction.UP,ordinary,false))).consumesAction(), "Ordinary fire disabled");
        report.addProperty("ordinaryFire", true);
        var bed = base.offset(12, 1, 0);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 2; z++) {
            level.setBlockAndUpdate(bed.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 3; y++) level.setBlockAndUpdate(bed.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        level.setBlockAndUpdate(bed, foot);
        level.setBlockAndUpdate(bed.south(), foot.setValue(BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        player.setRespawnPosition(Level.OVERWORLD, bed, 0, false, false);
        var bedRespawn = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        check(bedRespawn.newLevel() == level && bedRespawn.pos().distanceToSqr(Vec3.atCenterOf(bed)) < 25,
                "Valid personal bed lost priority");
        report.addProperty("nativePersonalBedPreserved", true);
        var messages = new ArrayList<Component>();
        var source = server.createCommandSourceStack().withSource(new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        });
        synchronousStructureCalls = 0;
        try { server.getCommands().getDispatcher().execute("locate structure minecraft:ruined_portal",source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            check(!enabled, "Enabled guidance threw instead of returning a bounded suggestion");
        }
        check(synchronousStructureCalls == (enabled ? 0 : 1), "Unexpected native synchronous lookup invocation");
        if (enabled) check(messages.stream().anyMatch(m -> m.getString().contains("/skyweavecompat structures")), "Missing bounded guidance");
        report.addProperty("synchronousStructureCalls", synchronousStructureCalls);
        messages.clear();
        if (enabled) {
            check(server.getCommands().getDispatcher().execute("locate structure #minecraft:village", source) == 0, "Guidance returned success");
            check(messages.stream().anyMatch(m -> m.getString().contains("structure=#minecraft:village")), "Tag was lost from suggestion");
            check(server.getCommands().getDispatcher().execute("execute in minecraft:overworld run locate structure minecraft:ruined_portal", source) == 0,
                    "Redirected marked lookup bypassed guidance");
        } else expectCommandFailure(server, source, "locate structure #minecraft:village");
        int before = synchronousStructureCalls;
        expectCommandFailure(server, source, "locate structure minecraft:not_registered");
        expectCommandFailure(server, source.withPermission(0), "locate structure minecraft:ruined_portal");
        check(synchronousStructureCalls == before, "Invalid or unprivileged query reached lookup");
        expectCommandFailure(server, source, "execute in minecraft:the_end run locate structure minecraft:ruined_portal");
        check(synchronousStructureCalls == before + 1, "Foreign effective dimension was intercepted");
        synchronousBiomeCalls = synchronousPoiCalls = 0;
        expectCommandFailure(server, source, "locate biome minecraft:plains");
        expectCommandFailure(server, source, "locate poi minecraft:home");
        check(synchronousBiomeCalls == 1 && synchronousPoiCalls == 1, "Biome/POI branches were changed");
        report.addProperty("nativeSelectorPermissionDimensionControls", true);
        report.addProperty("nativeBiomeAndPoiPreserved", true);
        if (enabled) {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 23));
            var saved = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            server.getPlayerList().remove(player);
            saved.putString("Dimension", "minecraft:the_nether");
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                    .resolve(cookie.gameProfile().getId() + ".dat");
            net.minecraft.nbt.NbtIo.writeCompressed(saved, path);
            player = ProbePlayers.login(server, cookie);
            check(player.serverLevel() == level, "Saved Nether login did not recover");
            check(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 23,
                    "Login recovery changed inventory");
            report.addProperty("savedNetherLoginRecovered", true);
        }
        server.getPlayerList().remove(player);
        var templates = server.getStructureManager(); int ruins = 0;
        for (var id : templates.listTemplates().filter(id -> id.getPath().contains("ruined_portal")).toList()) {
            var nbt = templates.get(id).orElseThrow().save(new net.minecraft.nbt.CompoundTag());
            // Native saved template palettes identify actual placed block states.
            check(!nbt.toString().contains("minecraft:nether_portal"), "Active portal template: " + id);
            ruins++;
        }
        check(ruins > 0, "No ruined portal templates inspected");
        report.addProperty("inertRuinedPortalTemplates", ruins);
        report.addProperty("policyEnabled", enabled);
        report.addProperty("complete", true);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void expectCommandFailure(MinecraftServer server, CommandSourceStack source, String command) throws Exception {
        try {
            server.getCommands().getDispatcher().execute(command, source);
            throw new AssertionError("Expected native command failure: " + command);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
    }
}
