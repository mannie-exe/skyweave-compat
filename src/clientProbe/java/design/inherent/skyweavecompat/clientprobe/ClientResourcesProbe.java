package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Finite resource comparisons in a marked disposable integrated-server fixture. */
final class ClientResourcesProbe {
    private final Path root = FMLPaths.GAMEDIR.get();
    private final ArrayList<String> baseline;
    private int ticks, sequence = -1;
    private String error;
    private boolean opened;
    private com.google.gson.JsonObject modelReport;
    private final ClientWorldTextProbe worldText;
    ClientResourcesProbe() {
        if (!Files.isRegularFile(root.resolve("resource-review-fixture.json"))) throw new IllegalStateException("Resource probe requires its disposable marker");
        // A reopened fixture must acknowledge, rather than replay, its last command.
        var previous = root.resolve("resource-review-control.json");
        if (Files.isRegularFile(previous)) {
            try { sequence = JsonParser.parseString(Files.readString(previous)).getAsJsonObject().get("sequence").getAsInt(); }
            catch (Exception invalid) { throw new IllegalStateException("Unreadable resource probe control", invalid); }
        }
        baseline = new ArrayList<>(Minecraft.getInstance().options.resourcePacks);
        baseline.removeIf(id -> id.startsWith("file/review-"));
        worldText = Files.isRegularFile(root.resolve("world-text-review.json")) && net.neoforged.fml.ModList.get().isLoaded("iris")
                ? new ClientWorldTextProbe() : null;
        NeoForge.EVENT_BUS.addListener(this::tick);
        if (Boolean.getBoolean("skyweave_compat.probe.terrain_capture")) {
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) -> {
                // Inspection photography only; never installed in the shipping mod.
                event.setNearPlaneDistance(8192); event.setFarPlaneDistance(8193); event.setCanceled(true);
            });
        }
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (++ticks % 10 != 0 || mc.getOverlay() != null) return;
        // An explicit property creates/opens only this marked disposable fixture.
        String world = System.getProperty("skyweave_compat.probe.resources.world");
        if (!opened && world != null && mc.screen instanceof TitleScreen) {
            if (!world.matches("resource-review-[a-z0-9-]{1,48}")
                    || !Files.isRegularFile(root.resolve("saves").resolve(world).resolve(".resource-review-world")))
                throw new IllegalStateException("Resource world needs its explicit fixture marker");
            opened = true;
            if (Files.isRegularFile(root.resolve("saves").resolve(world).resolve("level.dat")))
                mc.createWorldOpenFlows().openWorld(world, () -> mc.setScreen(new TitleScreen()));
            else mc.createWorldOpenFlows().createFreshLevel(world,
                    new LevelSettings("Resource repair review", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
        }
        try {
            var path = root.resolve("resource-review-control.json");
            if (Files.isRegularFile(path) && Files.size(path) < 4096) {
                var command = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (command.get("sequence").getAsInt() != sequence) {
                    sequence = command.get("sequence").getAsInt(); error = null;
                    switch (command.get("operation").getAsString()) {
                        case "packs" -> {
                            var selected = new ArrayList<>(baseline);
                            String treatment = command.get("treatment").getAsString();
                            if (!treatment.equals("baseline")) {
                                if (!treatment.matches("review-[a-z0-9-]+\\.zip") || !Files.isRegularFile(root.resolve("resourcepacks").resolve(treatment))) throw new IllegalArgumentException("Unknown review treatment");
                                selected.add("file/" + treatment);
                            }
                            mc.getResourcePackRepository().reload();
                            var actual = new ArrayList<String>(mc.getResourcePackRepository().getSelectedIds());
                            actual.removeIf(id -> id.startsWith("file/review-"));
                            for (String id : selected) if (!actual.contains(id)) actual.add(id);
                            mc.getResourcePackRepository().setSelected(actual);
                            mc.options.resourcePacks.clear(); mc.options.resourcePacks.addAll(selected);
                            mc.reloadResourcePacks();
                        }
                        case "command" -> {
                            if (mc.getSingleplayerServer() == null || mc.player == null) throw new IllegalStateException("Expected isolated integrated world");
                            var server = mc.getSingleplayerServer();
                            var playerId = mc.player.getUUID();
                            String text = command.get("command").getAsString();
                            server.execute(() -> {
                                var player = server.getPlayerList().getPlayer(playerId);
                                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), text);
                            });
                        }
                        case "interact" -> {
                            var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
                            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                        }
                        case "break" -> mc.gameMode.startDestroyBlock(new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt()), Direction.UP);
                        case "settings" -> mc.setScreen(new design.inherent.skyweavecompat.client.ConfigurationOverview(
                                net.neoforged.fml.ModList.get().getModContainerById("skyweave_compat").orElseThrow(), mc.screen));
                        case "close" -> mc.player.closeContainer();
                        case "hide" -> { mc.options.hideGui = command.get("value").getAsBoolean(); mc.options.pauseOnLostFocus = false; }
                        case "shader_rebuild" -> {
                            if (!design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.SHADER_PATCHES)) throw new IllegalStateException("Shader assembly is disabled");
                            design.inherent.skyweavecompat.client.ShaderAssembly.requestRebuild();
                            net.irisshaders.iris.Iris.reload();
                        }
                        case "shaders" -> {
                            if (!net.neoforged.fml.ModList.get().isLoaded("iris"))
                                throw new IllegalStateException("Shader switching requires Iris in the review fixture");
                            if (command.has("pack")) net.irisshaders.iris.Iris.getIrisConfig().setShaderPackName(command.get("pack").getAsString());
                            net.irisshaders.iris.Iris.getIrisConfig().setShadersEnabled(command.get("value").getAsBoolean());
                            net.irisshaders.iris.Iris.getIrisConfig().save(); net.irisshaders.iris.Iris.reload();
                        }
                        case "rejoin" -> {
                            if (world == null) throw new IllegalStateException("Expected a marked review world");
                            var server = mc.getSingleplayerServer();
                            if (server != null) server.halt(false);
                            mc.disconnect(new TitleScreen());
                            opened = false;
                        }
                        case "quit" -> {
                            var server = mc.getSingleplayerServer();
                            if (server != null) server.halt(false);
                            mc.stop();
                        }
                        case "world_text_controls" -> {
                            if (worldText == null) throw new IllegalStateException("Expected world-text fixture marker and Iris");
                            worldText.control(command.get("value").getAsBoolean());
                        }
                        case "unicode" -> mc.options.forceUnicodeFont().set(command.get("value").getAsBoolean());
                        case "smooth_lighting" -> {
                            mc.options.ambientOcclusion().set(command.get("value").getAsBoolean());
                            mc.levelRenderer.allChanged();
                        }
                        case "world_text_snapshot" -> {
                            if (worldText == null) throw new IllegalStateException("Expected world-text fixture marker and Iris");
                            modelReport = worldText.snapshot(command.has("remember") && command.get("remember").getAsBoolean());
                        }
                        case "snapshot" -> {}
                        case "environment" -> {
                            if (mc.level == null || mc.player == null) throw new IllegalStateException("No environment fixture world");
                            var position = mc.gameRenderer.getMainCamera().getBlockPosition();
                            modelReport = new com.google.gson.JsonObject();
                            modelReport.addProperty("dimensionType", mc.level.dimensionTypeRegistration().getRegisteredName());
                            modelReport.addProperty("cloudHeight", mc.level.effects().getCloudHeight());
                            modelReport.addProperty("openSky", design.inherent.skyweavecompat.client.IslandSkyExposure.openSky());
                            modelReport.addProperty("skyLight", mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, position));
                            modelReport.addProperty("biome", mc.level.getBiome(position).getRegisteredName());
                            modelReport.addProperty("rawAltitudePrecipitation", mc.level.getBiome(position).value().getPrecipitationAt(position).name());
                            modelReport.addProperty("nativeRainAt", mc.level.isRainingAt(position));
                            modelReport.add("fogColor", new com.google.gson.Gson().toJsonTree(com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor()));
                            if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
                                var query = Class.forName("net.irisshaders.iris.uniforms.BiomeUniforms")
                                        .getDeclaredMethod("lambda$addBiomeUniforms$2", net.minecraft.client.player.LocalPlayer.class);
                                query.setAccessible(true);
                                modelReport.addProperty("irisPrecipitation", (int)query.invoke(null, mc.player));
                            }
                        }
                        case "terrain_ready" -> modelReport = terrainReadiness(command);
                        case "view_distance" -> {
                            int distance = command.get("value").getAsInt();
                            if (distance < 2 || distance > 32) throw new IllegalArgumentException("Fixture view distance must be 2..32");
                            mc.options.renderDistance().set(distance);
                            mc.options.broadcastOptions();
                            mc.levelRenderer.allChanged();
                        }
                        case "water_snapshot" -> {
                            var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
                            var fluid = mc.level.getFluidState(pos);
                            if (!fluid.is(net.minecraft.tags.FluidTags.WATER)) throw new IllegalStateException("Water fixture is not delivered: " + pos);
                            net.minecraft.world.level.BlockGetter region = new net.minecraft.world.level.BlockGetter() {
                                public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p) { return mc.level.getBlockEntity(p); }
                                public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p) { return mc.level.getBlockState(p); }
                                public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p) { return mc.level.getFluidState(p); }
                                public int getHeight() { return mc.level.getHeight(); }
                                public int getMinBuildHeight() { return mc.level.getMinBuildHeight(); }
                            };
                            modelReport = new com.google.gson.JsonObject();
                            modelReport.addProperty("state", mc.level.getBlockState(pos).toString());
                            modelReport.addProperty("biome", mc.level.getBiome(pos).getRegisteredName());
                            modelReport.addProperty("levelFlow", fluid.getFlow(mc.level, pos).toString());
                            modelReport.addProperty("regionFlow", fluid.getFlow(region, pos).toString());
                        }
                        case "structure_records" -> modelReport = ClientStructureRecordsProbe.observe(mc,
                                command.get("chunkX").getAsInt(), command.get("chunkZ").getAsInt(),
                                command.get("structure").getAsString());
                        case "distance" -> modelReport = ClientDistanceProbe.observe(command);
                        case "assembly" -> modelReport = ClientAssemblyProbe.observe(command);
                        case "ray" -> {
                            var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
                            var a = command.getAsJsonArray("from"); var b = command.getAsJsonArray("to");
                            var from = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
                            var to = new Vec3(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble());
                            var kind = command.has("collider") && command.get("collider").getAsBoolean()
                                    ? net.minecraft.world.level.ClipContext.Block.COLLIDER : net.minecraft.world.level.ClipContext.Block.OUTLINE;
                            var state = mc.level.getBlockState(pos);
                            var shape = state.getShape(mc.level, pos);
                            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, kind,
                                    net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                            var direct = shape.clip(from, to, pos);
                            modelReport = new com.google.gson.JsonObject();
                            modelReport.addProperty("state", state.toString());
                            modelReport.addProperty("hit", hit.getType().name());
                            modelReport.addProperty("block", hit.getBlockPos().toShortString());
                            modelReport.addProperty("point", hit.getLocation().toString());
                            modelReport.addProperty("distance", from.distanceTo(hit.getLocation()));
                            modelReport.addProperty("directHit", direct != null);
                            modelReport.addProperty("emptyCollision", state.getCollisionShape(mc.level, pos).isEmpty());
                            modelReport.add("outline", new com.google.gson.Gson().toJsonTree(shape.toAabbs()));
                        }
                        case "glass_targets" -> {
                            modelReport = new com.google.gson.JsonObject();
                            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                                if (id.getNamespace().equals("minecraft")
                                        || !design.inherent.skyweavecompat.client.GlassBackfaces.fusionTarget(block.defaultBlockState())) continue;
                                String type = block.getClass().getName();
                                if (!modelReport.has(type)) modelReport.add(type, new com.google.gson.JsonArray());
                                modelReport.getAsJsonArray(type).add(id.toString());
                            }
                        }
                        case "inspect" -> {
                            if (mc.level == null) throw new IllegalStateException("Expected isolated world");
                            var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
                            var state = mc.level.getBlockState(pos);
                            var model = mc.getBlockRenderer().getBlockModel(state);
                            modelReport = new com.google.gson.JsonObject();
                            modelReport.addProperty("state", state.toString());
                            modelReport.addProperty("modelClass", model.getClass().getName());
                            var data = model.getModelData(mc.level, pos, state,
                                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                            var bounds = new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
                            var counts = new com.google.gson.JsonObject();
                            var culling = new com.google.gson.JsonObject();
                            var visible = new com.google.gson.JsonObject();
                            var surfaces = new com.google.gson.JsonObject();
                            for (int side = -1; side < 6; side++) {
                                var direction = side == -1 ? null : Direction.values()[side];
                                var quads = model.getQuads(state, direction, net.minecraft.util.RandomSource.create(42), data, null);
                                String label = direction == null ? "unculled" : direction.getName();
                                counts.addProperty(label, quads.size());
                                if (direction != null) {
                                    culling.addProperty(label, state.skipRendering(mc.level.getBlockState(pos.relative(direction)), direction));
                                    visible.addProperty(label, net.minecraft.world.level.block.Block.shouldRenderFace(state, mc.level, pos, direction, pos.relative(direction)));
                                }
                                var samples = new com.google.gson.JsonArray();
                                for (var quad : quads) {
                                    var vertices = quad.getVertices();
                                    var sample = new com.google.gson.JsonObject();
                                    var sprite = quad.getSprite();
                                    sample.addProperty("sprite", sprite.contents().name().toString());
                                    sample.addProperty("face", quad.getDirection().getName());
                                    sample.addProperty("quadClass", quad.getClass().getName());
                                    sample.addProperty("tint", quad.getTintIndex());
                                    sample.addProperty("shade", quad.isShade());
                                    sample.addProperty("ambientOcclusion", quad.hasAmbientOcclusion());
                                    var positions = new com.google.gson.JsonArray();
                                    var uv = new com.google.gson.JsonArray();
                                    for (int vertex = 0; vertex < 4; vertex++) {
                                        int offset = vertex * (vertices.length / 4);
                                        for (int axis = 0; axis < 3; axis++)
                                            positions.add(Float.intBitsToFloat(vertices[offset + axis]));
                                        uv.add(16 * (Float.intBitsToFloat(vertices[offset + 4]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()));
                                        uv.add(16 * (Float.intBitsToFloat(vertices[offset + 5]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
                                    }
                                    sample.add("positions", positions);
                                    sample.add("uv", uv); samples.add(sample);
                                    for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                                        double value = Float.intBitsToFloat(vertices[vertex * (vertices.length / 4) + axis]);
                                        bounds[axis] = Math.min(bounds[axis], value);
                                        bounds[axis + 3] = Math.max(bounds[axis + 3], value);
                                    }
                                }
                                surfaces.add(label, samples);
                            }
                            if (command.has("benchmark")) {
                                int iterations = Math.clamp(command.get("benchmark").getAsInt(), 1, 2000);
                                long started = System.nanoTime();
                                int emitted = 0;
                                var random = net.minecraft.util.RandomSource.create(42);
                                for (int iteration = 0; iteration < iterations; iteration++)
                                    for (var direction : Direction.values()) {
                                        random.setSeed(42);
                                        emitted += model.getQuads(state, direction, random, data, null).size();
                                    }
                                modelReport.addProperty("benchmarkIterations", iterations);
                                modelReport.addProperty("benchmarkQuads", emitted);
                                modelReport.addProperty("benchmarkMillis", (System.nanoTime() - started) / 1_000_000.0);
                                started = System.nanoTime();
                                for (int iteration = 0; iteration < iterations; iteration++)
                                    model.getModelData(mc.level, pos, state,
                                            net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                                modelReport.addProperty("modelDataMillis", (System.nanoTime() - started) / 1_000_000.0);
                            }
                            modelReport.add("quads", counts); modelReport.add("skipNeighborFace", culling);
                            modelReport.add("renderFace", visible); modelReport.add("surfaces", surfaces);
                            if (Double.isFinite(bounds[0])) modelReport.add("bounds", new com.google.gson.Gson().toJsonTree(bounds));
                            try {
                                var config = Class.forName("ca.fxco.moreculling.MoreCulling").getField("CONFIG").get(null);
                                modelReport.addProperty("moreCullingBlockStates", config.getClass().getField("useBlockStateCulling").getBoolean(config));
                            } catch (ReflectiveOperationException absent) { modelReport.addProperty("moreCullingBlockStates", "unavailable"); }
                        }
                        default -> throw new IllegalArgumentException("Unknown resource action");
                    }
                }
            }
        } catch (Exception failure) { error = failure.toString(); }
        try {
            var report = new com.google.gson.JsonObject();
            report.addProperty("pid", ProcessHandle.current().pid()); report.addProperty("sequence", sequence); report.addProperty("ticks", ticks);
            report.addProperty("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
            report.addProperty("world", mc.level != null);
            report.addProperty("shaders", net.neoforged.fml.ModList.get().isLoaded("iris") && net.irisshaders.iris.Iris.isPackInUseQuick());
            report.addProperty("width",mc.getWindow().getWidth()); report.addProperty("height",mc.getWindow().getHeight());
            report.add("packs", new com.google.gson.Gson().toJsonTree(mc.getResourcePackRepository().getSelectedIds()));
            if (modelReport != null) report.add("model", modelReport);
            if (mc.player != null) {
                report.addProperty("x",mc.player.getX());report.addProperty("y",mc.player.getY());report.addProperty("z",mc.player.getZ());
                report.addProperty("yaw",mc.player.getYRot());report.addProperty("pitch",mc.player.getXRot());
                report.addProperty("menu",mc.player.containerMenu.getClass().getName());report.addProperty("slots",mc.player.containerMenu.slots.size());
            }
            if (error != null) report.addProperty("error",error);
            ProbeStateFiles.write(root.resolve("resource-review-state.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static com.google.gson.JsonObject terrainReadiness(com.google.gson.JsonObject command) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("skyweave_compat.probe.terrain_capture")
                && !Boolean.getBoolean("skyweave_compat.probe.terrain_readiness"))
            throw new IllegalStateException("Enable isolated terrain readiness or capture checks");
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Expected an isolated integrated world");
        int x = command.get("chunkX").getAsInt(), z = command.get("chunkZ").getAsInt();
        int radius = command.get("radius").getAsInt();
        if (radius < 1 || radius > 30) throw new IllegalArgumentException("Capture radius must be 1..30 chunks");
        var rendererType = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
        var renderer = rendererType.getMethod("instance").invoke(null);
        var sectionReady = rendererType.getMethod("isSectionReady", int.class, int.class, int.class);
        int expected = 0, loaded = 0, occupied = 0, visibleTops = 0, topsReady = 0;
        var missing = new com.google.gson.JsonArray();
        var unready = new com.google.gson.JsonArray();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            expected++;
            var chunk = mc.level.getChunkSource().getChunk(x + dx, z + dz,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            if (chunk == null) {
                if (missing.size() < 8) missing.add((x + dx) + "," + (z + dz));
                continue;
            }
            loaded++;
            var sections = chunk.getSections();
            for (int index = sections.length - 1; index >= 0; index--) {
                if (sections[index].hasOnlyAir()) continue;
                occupied++;
                int sectionY = chunk.getMinSection() + index;
                var box = new net.minecraft.world.phys.AABB((x + dx) * 16, sectionY * 16, (z + dz) * 16,
                        (x + dx + 1) * 16, (sectionY + 1) * 16, (z + dz + 1) * 16);
                if (mc.levelRenderer.getFrustum().isVisible(box)) {
                    visibleTops++;
                    if ((boolean)sectionReady.invoke(renderer, x + dx, sectionY, z + dz)) topsReady++;
                    else if (unready.size() < 8) {
                        var sample = new com.google.gson.JsonObject();
                        sample.addProperty("chunkX", x + dx); sample.addProperty("sectionY", sectionY); sample.addProperty("chunkZ", z + dz);
                        var blocks = new java.util.LinkedHashSet<String>();
                        for (int sx = 0; sx < 16 && blocks.size() < 3; sx++)
                            for (int sy = 0; sy < 16 && blocks.size() < 3; sy++)
                                for (int sz = 0; sz < 16 && blocks.size() < 3; sz++) {
                                    var state = sections[index].getBlockState(sx, sy, sz);
                                    if (!state.isAir()) blocks.add(state.toString());
                                }
                        sample.add("blocks", new com.google.gson.Gson().toJsonTree(blocks)); unready.add(sample);
                    }
                }
                break;
            }
        }
        var result = new com.google.gson.JsonObject();
        result.addProperty("expectedChunks", expected); result.addProperty("loadedChunks", loaded);
        boolean queueEmpty = (boolean)rendererType.getMethod("isTerrainRenderComplete").invoke(renderer);
        result.addProperty("occupiedChunks", occupied); result.addProperty("visibleTopSections", visibleTops);
        result.addProperty("topMeshesReady", topsReady); result.addProperty("meshQueueEmpty", queueEmpty);
        result.addProperty("nativeDistance", mc.options.renderDistance().get());
        result.addProperty("ready", loaded == expected && topsReady == visibleTops && queueEmpty);
        result.add("missingSample", missing); result.addProperty("renderer", rendererType.getMethod("getChunksDebugString").invoke(renderer).toString());
        result.add("unreadyTopSample", unready);
        return result;
    }
}
