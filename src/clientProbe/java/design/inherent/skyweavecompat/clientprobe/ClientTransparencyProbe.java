package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Read actual delivered material data and final layer/cull output in the owned resource world. */
final class ClientTransparencyProbe {
    private static String pendingCapture;
    private ClientTransparencyProbe() {}

    static void capture(Minecraft mc, JsonObject command) {
        if (mc.getSingleplayerServer() == null || mc.level == null || mc.screen != null || pendingCapture != null)
            throw new IllegalStateException("Expected the ready isolated world, without a screen or pending capture");
        String name = command.get("name").getAsString();
        if (!name.matches("transparency-[a-z0-9-]{1,80}\\.png")) throw new IllegalArgumentException("Invalid capture name");
        pendingCapture = name;
    }

    static void afterFrame(Minecraft mc) {
        if (pendingCapture == null) return;
        String name = pendingCapture; pendingCapture = null;
        var receipt = new JsonObject(); receipt.addProperty("name", name);
        try {
            if (mc.screen != null || mc.getOverlay() != null || mc.level == null || mc.player == null)
                throw new IllegalStateException("Capture interrupted by a screen or world change");
            if (net.neoforged.fml.ModList.get().isLoaded("sodium")) {
                var renderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
                if (!(boolean) renderer.getMethod("isTerrainRenderComplete").invoke(renderer.getMethod("instance").invoke(null)))
                    throw new IllegalStateException("Sodium mesh queue not finished");
            }
            receipt.addProperty("screen", "none"); receipt.addProperty("shaders", net.neoforged.fml.ModList.get().isLoaded("iris") && net.irisshaders.iris.Iris.isPackInUseQuick());
            receipt.addProperty("x", mc.player.getX()); receipt.addProperty("y", mc.player.getY()); receipt.addProperty("z", mc.player.getZ());
            receipt.addProperty("yaw", mc.player.getYRot()); receipt.addProperty("pitch", mc.player.getXRot());
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {});
            receipt.addProperty("accepted", true);
        } catch (Exception failure) { receipt.addProperty("accepted", false); receipt.addProperty("error", failure.toString()); }
        try {
            String json = new Gson().toJson(receipt);
            ProbeStateFiles.write(mc.gameDirectory.toPath().resolve("transparency-capture.json"), json);
            ProbeStateFiles.write(mc.gameDirectory.toPath().resolve("screenshots").resolve(name.substring(0, name.length() - 4) + ".json"), json);
        }
        catch (java.io.IOException invalid) { throw new IllegalStateException(invalid); }
    }

    static JsonObject material(Minecraft mc, JsonObject command) throws Exception {
        var server = mc.getSingleplayerServer();
        if (server == null || !Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(".resource-review-world")))
            throw new IllegalStateException("Expected the marked isolated resource world");
        var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
        if (Math.abs((long) pos.getX()) > 256 || Math.abs((long) pos.getZ()) > 256)
            throw new IllegalArgumentException("Subject exceeds the fixture boundary");
        var id = net.minecraft.resources.ResourceLocation.parse(command.get("material").getAsString());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown fixture material");
        return server.submit(() -> {
            var level = server.overworld();
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) throw new IllegalStateException("Subject is not loaded");
            var entity = level.getBlockEntity(pos);
            if (entity == null) throw new IllegalStateException("Expected a Copycat block entity");
            var type = entity.getClass();
            try {
                var material = BuiltInRegistries.BLOCK.get(id);
                var consumed = new net.minecraft.world.item.ItemStack(material);
                if (command.has("part")) {
                    String part = command.get("part").getAsString();
                    type.getMethod("setMaterial", String.class, BlockState.class).invoke(entity, part, material.defaultBlockState());
                    type.getMethod("setConsumedItem", String.class, net.minecraft.world.item.ItemStack.class).invoke(entity, part, consumed);
                } else {
                    type.getMethod("setMaterial", BlockState.class).invoke(entity, material.defaultBlockState());
                    type.getMethod("setConsumedItem", net.minecraft.world.item.ItemStack.class).invoke(entity, consumed);
                }
                type.getMethod("notifyUpdate").invoke(entity);
            } catch (ReflectiveOperationException invalid) { throw new IllegalStateException("Native material setter failed", invalid); }
            var result = new JsonObject();
            result.addProperty("entityClass", type.getName()); result.addProperty("position", pos.toShortString());
            result.addProperty("material", id.toString());
            return result;
        }).get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    static JsonObject connectedTextures(Minecraft mc, JsonObject command) throws Exception {
        var server = mc.getSingleplayerServer();
        if (server == null || !Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(".resource-review-world")))
            throw new IllegalStateException("Expected owned world");
        var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
        if (Math.abs((long) pos.getX()) > 256 || Math.abs((long) pos.getZ()) > 256) throw new IllegalArgumentException("Fixture bounds");
        return server.submit(() -> {
            var level = server.overworld();
            if (!level.hasChunkAt(pos)) throw new IllegalStateException("Expected loaded subject");
            var be = level.getBlockEntity(pos);
            if (be == null) throw new IllegalStateException("Expected Copycat subject");
            try {
                boolean value = command.get("value").getAsBoolean();
                if (command.has("part")) be.getClass().getMethod("setEnableCT", String.class, boolean.class).invoke(be, command.get("part").getAsString(), value);
                else be.getClass().getMethod("setCTEnabled", boolean.class).invoke(be, value);
                be.getClass().getMethod("notifyUpdate").invoke(be);
                var result = new JsonObject(); result.addProperty("ct", value); result.addProperty("position", pos.toShortString()); return result;
            } catch (ReflectiveOperationException invalid) { throw new IllegalStateException("Native CT setter failed", invalid); }
        }).get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    static JsonObject inspect(Minecraft mc, JsonObject command) throws Exception {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || mc.player == null
                || !Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(".resource-review-world")))
            throw new IllegalStateException("Expected the marked isolated resource world");
        var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
        if (Math.abs((long) pos.getX()) > 256 || Math.abs((long) pos.getZ()) > 256
                || mc.level.isOutsideBuildHeight(pos) || !mc.level.hasChunkAt(pos))
            throw new IllegalArgumentException("Expected a loaded subject within the bounded resource fixture");
        var state = mc.level.getBlockState(pos);
        var model = mc.getBlockRenderer().getBlockModel(state);
        // ClientLevel's native manager refreshes the existing section's delivered BE data.
        // EMPTY is legitimate for static blocks, but cannot qualify a Copycat material.
        var incoming = mc.level.getModelData(pos);
        var data = model.getModelData(mc.level, pos, state, incoming);
        var materials = new JsonObject();
        Method reader = null;
        for (String name : new String[]{"getMaterials", "getMaterial"}) {
            try { reader = model.getClass().getMethod(name, ModelData.class); break; }
            catch (NoSuchMethodException ignored) {}
        }
        if (reader != null) {
            Object value = reader.invoke(null, data);
            if (value instanceof BlockState material) materials.addProperty("material", BuiltInRegistries.BLOCK.getKey(material.getBlock()).toString());
            else if (value instanceof Map<?, ?> map) for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof BlockState material))
                    throw new IllegalStateException("Unexpected native material-map contract");
                materials.addProperty(key, BuiltInRegistries.BLOCK.getKey(material.getBlock()).toString());
            }
        }
        if (command.has("material")) {
            String expected = command.get("material").getAsString();
            if (incoming == ModelData.EMPTY || materials.entrySet().stream().noneMatch(entry -> expected.equals(entry.getValue().getAsString())))
                throw new IllegalStateException("Expected delivered Copycat material " + expected + ", observed " + materials);
        }
        var report = new JsonObject();
        report.addProperty("position", pos.toShortString());
        report.addProperty("state", state.toString());
        report.addProperty("modelClass", model.getClass().getName());
        report.addProperty("incomingModelDataEmpty", incoming == ModelData.EMPTY);
        report.addProperty("incomingModelDataIdentity", System.identityHashCode(incoming));
        report.addProperty("gatheredModelDataIdentity", System.identityHashCode(data));
        report.add("materials", materials);
        var contacts = data.get(design.inherent.skyweavecompat.client.GlassSurfaceContacts.PROPERTY);
        report.addProperty("contactSnapshot", contacts != null);
        if (contacts != null) try {
            var captured = (java.util.List<?>) contacts.getClass().getMethod("faces").invoke(contacts);
            report.addProperty("contactFaces", captured.size());
            var contactMaterials = new java.util.TreeSet<String>();
            for (var contact : captured) {
                var owner = contact.getClass().getMethod("owner").invoke(contact);
                Object material = owner.getClass().getMethod("material").invoke(owner);
                if (material instanceof BlockState source) contactMaterials.add(BuiltInRegistries.BLOCK.getKey(source.getBlock()).toString());
            }
            report.add("contactMaterials", new Gson().toJsonTree(contactMaterials));
        } catch (NoSuchMethodException baseline) { report.addProperty("legacyBoundaryContacts", true); }
        try { report.addProperty("modelResourceGeneration", (long) model.getClass().getMethod("skyweave$resourceGeneration").invoke(model)); }
        catch (NoSuchMethodException baseline) {}
        var groups = new JsonArray();
        long seed = state.getSeed(pos);
        var random = RandomSource.create(seed);
        int total = 0;
        for (var layer : model.getRenderTypes(state, random, data)) {
            for (int side = -1; side < 6; side++) {
                Direction direction = side == -1 ? null : Direction.values()[side];
                random.setSeed(seed);
                var quads = model.getQuads(state, direction, random, data, layer);
                total += quads.size();
                if (total > 4096) throw new IllegalStateException("Fixture quad budget exceeded");
                var group = new JsonObject();
                group.addProperty("layer", layer.toString());
                group.addProperty("cullGroup", direction == null ? "unculled" : direction.getName());
                if (direction != null) group.addProperty("nativeFaceVisible", net.minecraft.world.level.block.Block.shouldRenderFace(
                        state, mc.level, pos, direction, pos.relative(direction)));
                var faces = new JsonArray();
                for (var quad : quads) {
                    var face = new JsonObject();
                    face.addProperty("direction", quad.getDirection().getName());
                    face.addProperty("sprite", quad.getSprite().contents().name().toString());
                    face.addProperty("quadClass", quad.getClass().getName());
                    face.addProperty("tint", quad.getTintIndex());
                    face.addProperty("shade", quad.isShade());
                    face.addProperty("ambientOcclusion", quad.hasAmbientOcclusion());
                    face.add("vertices", new Gson().toJsonTree(quad.getVertices()));
                    faces.add(face);
                }
                group.add("faces", faces); groups.add(group);
            }
        }
        report.addProperty("totalQuads", total);
        report.add("groups", groups);
        if (command.has("cachePhases") && command.get("cachePhases").getAsBoolean()) {
            net.minecraft.client.renderer.RenderType layer = null;
            int largest = 0;
            for (var candidate : model.getRenderTypes(state, RandomSource.create(seed), data)) {
                int size = query(model, state, data, candidate, RandomSource.create(seed), seed).size();
                if (size > largest) { largest = size; layer = candidate; }
            }
            if (layer == null || largest == 0) throw new IllegalStateException("Phase control needs actual emitted geometry");
            var shared = RandomSource.create(seed);
            var a = query(model, state, data, layer, shared, seed);
            JsonArray b;
            try (var scope = sourceScope()) { b = query(model, state, data, layer, shared, seed); }
            var aa = query(model, state, data, layer, shared, seed);
            var fresh = RandomSource.create(seed);
            JsonArray raw;
            try (var scope = sourceScope()) { raw = query(model, state, data, layer, fresh, seed); }
            var sourceThenFinal = query(model, state, data, layer, fresh, seed);
            var phases = new JsonObject();
            phases.addProperty("layer", layer.toString());
            phases.addProperty("normalBefore", a.size()); phases.addProperty("sourceSameKey", b.size());
            phases.addProperty("normalAfterSource", aa.size()); phases.addProperty("sourceFreshKey", raw.size());
            phases.addProperty("sourceThenFinal", sourceThenFinal.size());
            phases.addProperty("aBaStable", a.equals(aa));
            phases.addProperty("sourceIndependentOfCacheKey", b.equals(raw));
            phases.addProperty("finalIndependentOfPriorSource", a.equals(sourceThenFinal));
            phases.add("finalQuads", a); phases.add("originalQuads", raw);
            report.add("cachePhases", phases);
        }
        if (command.has("benchmark")) {
            int count = Math.clamp(command.get("benchmark").getAsInt(), 1, 200);
            var nativeBean = java.lang.management.ManagementFactory.getThreadMXBean();
            var allocation = nativeBean instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported() ? bean : null;
            boolean enabled = allocation != null && allocation.isThreadAllocatedMemoryEnabled();
            if (allocation != null && !enabled) allocation.setThreadAllocatedMemoryEnabled(true);
            try {
                int emitted = 0;
                for (int i = 0; i < 8; i++) model.getModelData(mc.level, pos, state, incoming);
                long thread = Thread.currentThread().threadId();
                long bytes = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread), start = System.nanoTime();
                for (int i = 0; i < count; i++) {
                    var prepared = model.getModelData(mc.level, pos, state, incoming);
                    var rng = RandomSource.create(seed);
                    for (var layer : model.getRenderTypes(state, rng, prepared)) for (int side = -1; side < 6; side++) {
                        rng.setSeed(seed);
                        emitted += model.getQuads(state, side < 0 ? null : Direction.values()[side], rng, prepared, layer).size();
                    }
                }
                long nanos = System.nanoTime() - start;
                var benchmark = new JsonObject(); benchmark.addProperty("iterations", count); benchmark.addProperty("emittedQuads", emitted);
                benchmark.addProperty("nanos", nanos); benchmark.addProperty("threadAllocatedBytes", allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - bytes);
                report.add("benchmark", benchmark);
            } finally { if (allocation != null && !enabled) allocation.setThreadAllocatedMemoryEnabled(false); }
        }
        return report;
    }

    static JsonObject profiles() throws Exception {
        var type = Class.forName("design.inherent.skyweavecompat.client.TransparencyProfiles");
        var snapshot = type.getMethod("current").invoke(null);
        var report = new JsonObject(); report.addProperty("generation", (long) snapshot.getClass().getMethod("generation").invoke(snapshot));
        report.addProperty("publishedGeneration", (long) type.getMethod("publishedGeneration").invoke(null));
        report.add("diagnostics", new Gson().toJsonTree(snapshot.getClass().getMethod("diagnostics").invoke(snapshot)));
        var counts = new JsonObject();
        var rules = (Map<?, ?>) snapshot.getClass().getMethod("rules").invoke(snapshot);
        for (var group : rules.entrySet()) {
            var profiles = new JsonObject();
            for (var raw : ((Map<?, ?>) group.getValue()).values()) {
                String id = raw.getClass().getMethod("profile").invoke(raw).toString();
                profiles.addProperty(id, profiles.has(id) ? profiles.get(id).getAsInt() + 1 : 1);
            }
            counts.add(group.getKey().toString(), profiles);
        }
        report.add("rules", counts);
        var diagnostics = Class.forName("design.inherent.skyweavecompat.client.TransparencyDiagnostics");
        report.add("fallbacks", new Gson().toJsonTree(diagnostics.getMethod("reported").invoke(null)));
        var resources = new JsonObject();
        for (var entry : Minecraft.getInstance().getResourceManager().listResources("transparency", id -> id.getPath().endsWith(".json")).entrySet()) {
            var resource = new JsonObject(); resource.addProperty("pack", entry.getValue().sourcePackId());
            try (var input = entry.getValue().open()) {
                byte[] bytes = input.readNBytes(65537);
                if (bytes.length <= 65536) resource.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                else resource.addProperty("oversize", true);
            }
            resources.add(entry.getKey().toString(), resource);
        }
        report.add("resources", resources);
        return report;
    }

    /** A real native ModelData call must enter our scope and unwind a failed view read. */
    static JsonObject exceptionScope(Minecraft mc, JsonObject command) throws Exception {
        if (mc.level == null || mc.getSingleplayerServer() == null) throw new IllegalStateException("Expected isolated world");
        var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
        if (Math.abs((long) pos.getX()) > 256 || Math.abs((long) pos.getZ()) > 256 || !mc.level.hasChunkAt(pos))
            throw new IllegalArgumentException("Expected bounded loaded subject");
        var state = mc.level.getBlockState(pos); var model = mc.getBlockRenderer().getBlockModel(state);
        var incoming = mc.level.getModelData(pos);
        if (incoming == ModelData.EMPTY || design.inherent.skyweavecompat.rendering.GeometryScope.sampling())
            throw new IllegalStateException("Expected delivered BE snapshot outside source scope");
        var failure = new IllegalStateException("owned transparency fixture view fault");
        boolean[] reached = {false};
        var view = (net.minecraft.world.level.BlockAndTintGetter) java.lang.reflect.Proxy.newProxyInstance(
                ClientTransparencyProbe.class.getClassLoader(), new Class<?>[]{net.minecraft.world.level.BlockAndTintGetter.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockState")
                            && design.inherent.skyweavecompat.rendering.GeometryScope.sampling()) {
                        reached[0] = true; throw failure;
                    }
                    try { return method.invoke(mc.level, args); }
                    catch (java.lang.reflect.InvocationTargetException wrapped) { throw wrapped.getCause(); }
                });
        boolean propagated = false;
        try { model.getModelData(view, pos, state, incoming); }
        catch (IllegalStateException actual) { if (actual != failure) throw actual; propagated = true; }
        if (!reached[0] || !propagated || design.inherent.skyweavecompat.rendering.GeometryScope.sampling())
            throw new IllegalStateException("Native view exception did not exercise and restore source scope");
        var prepared = model.getModelData(mc.level, pos, state, incoming);
        var report = new JsonObject(); report.addProperty("nativeSourceReadReached", true);
        report.addProperty("sameExceptionPropagated", true); report.addProperty("scopeRestored", true);
        report.addProperty("normalDataAfterFailure", prepared != ModelData.EMPTY);
        return report;
    }

    static JsonObject inventory(Minecraft mc) throws Exception {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) throw new IllegalStateException("Expected isolated world");
        var namespaces = java.util.Set.of("create", "copycats", "create_connected", "sim_copycats", "createpropulsion", "aeroworks", "linearbearing");
        var entries = new java.util.TreeMap<String, java.util.Set<String>>();
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), false, mc.level.registryAccess());
        for (var tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
            if (tab.getType() != net.minecraft.world.item.CreativeModeTab.Type.CATEGORY) continue;
            for (var stack : tab.getDisplayItems()) {
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (namespaces.contains(id.getNamespace()) && id.getPath().contains("copycat"))
                    entries.computeIfAbsent(id.toString(), ignored -> new java.util.TreeSet<>())
                            .add(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString());
            }
        }
        var report = new JsonObject(); report.add("creative", new Gson().toJsonTree(entries));
        if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
            var api = Class.forName("dev.emi.emi.api.EmiApi"); var stackType = Class.forName("dev.emi.emi.api.stack.EmiStack");
            var itemStack = stackType.getMethod("getItemStack"); var indexed = new java.util.TreeSet<String>();
            for (var raw : (java.util.List<?>) api.getMethod("getIndexStacks").invoke(null)) {
                var stack = (net.minecraft.world.item.ItemStack) itemStack.invoke(raw); var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (namespaces.contains(id.getNamespace()) && id.getPath().contains("copycat")) indexed.add(id.toString());
            }
            report.add("emiIndex", new Gson().toJsonTree(indexed));
            var managerType = Class.forName("dev.emi.emi.api.recipe.EmiRecipeManager"); var recipeType = Class.forName("dev.emi.emi.api.recipe.EmiRecipe");
            var manager = api.getMethod("getRecipeManager").invoke(null); var outputs = new java.util.TreeMap<String, Integer>();
            for (var recipe : (java.util.List<?>) managerType.getMethod("getRecipes").invoke(manager))
                for (var raw : (java.util.List<?>) recipeType.getMethod("getOutputs").invoke(recipe)) {
                    var stack = (net.minecraft.world.item.ItemStack) itemStack.invoke(raw); var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (namespaces.contains(id.getNamespace()) && id.getPath().contains("copycat")) outputs.merge(id.toString(), 1, Integer::sum);
                }
            report.add("recipeOutputs", new Gson().toJsonTree(outputs));
        }
        return report;
    }

    static JsonObject conversions(Minecraft mc) throws Exception {
        var server = mc.getSingleplayerServer();
        if (server == null || !Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(".resource-review-world")))
            throw new IllegalStateException("Expected marked isolated world");
        return server.submit(() -> {
            var report = new JsonObject(); var level = server.overworld();
            for (String shape : new String[]{"beam", "block", "board", "fence", "fence_gate", "slab", "stairs", "vertical_step", "wall"}) {
                String name = "copycat_" + shape;
                var oldId = net.minecraft.resources.ResourceLocation.parse("create_connected:" + name);
                var newId = net.minecraft.resources.ResourceLocation.parse("copycats:" + name);
                if (!BuiltInRegistries.ITEM.containsKey(oldId) || !BuiltInRegistries.ITEM.containsKey(newId))
                    throw new IllegalStateException("Missing conversion item " + name);
                var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 1,
                        java.util.List.of(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(oldId))));
                var recipe = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                        .orElseThrow(() -> new IllegalStateException("No native conversion " + name));
                var output = recipe.value().assemble(input, level.registryAccess());
                if (!BuiltInRegistries.ITEM.getKey(output.getItem()).equals(newId) || output.getCount() != 1
                        || !recipe.id().toString().equals("create_connected:crafting/palettes/" + name + "_compat"))
                    throw new IllegalStateException("Wrong native conversion " + name + ": " + recipe.id() + " -> " + output);
                report.addProperty(oldId.toString(), recipe.id().toString() + " -> " + newId);
            }
            return report;
        }).get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    static JsonObject sodiumSnapshot(Minecraft mc, JsonObject command) throws Exception {
        if (mc.level == null || mc.getSingleplayerServer() == null) throw new IllegalStateException("Expected isolated world");
        var pos = new BlockPos(command.get("x").getAsInt(), command.get("y").getAsInt(), command.get("z").getAsInt());
        if (Math.abs((long) pos.getX()) > 256 || Math.abs((long) pos.getZ()) > 256 || !mc.level.hasChunkAt(pos))
            throw new IllegalArgumentException("Expected bounded loaded subject");
        var sliceType = Class.forName("net.caffeinemc.mods.sodium.client.world.LevelSlice");
        var cacheType = Class.forName("net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache");
        var cache = cacheType.getConstructor(net.minecraft.world.level.Level.class).newInstance(mc.level);
        var context = sliceType.getMethod("prepare", net.minecraft.world.level.Level.class, net.minecraft.core.SectionPos.class, cacheType)
                .invoke(null, mc.level, net.minecraft.core.SectionPos.of(pos), cache);
        if (context == null) throw new IllegalStateException("Native section not available");
        var slice = sliceType.getConstructor(net.minecraft.client.multiplayer.ClientLevel.class).newInstance(mc.level);
        sliceType.getMethod("copyData", context.getClass()).invoke(slice, context);
        try {
            var view = (net.minecraft.world.level.BlockAndTintGetter) slice;
            var result = new JsonObject();
            result.addProperty("getterOwner", sliceType.getMethod("getModelData", BlockPos.class).getDeclaringClass().getName());
            for (int dx = 0; dx < 2; dx++) {
                var here = pos.offset(dx, 0, 0);
                Object snapshot = sliceType.getMethod("getPlatformModelData", BlockPos.class).invoke(slice, here);
                if (!(snapshot instanceof ModelData incoming)) throw new IllegalStateException("Unexpected native platform data");
                var observed = new JsonObject();
                observed.addProperty("nativeSnapshotEmpty", incoming == ModelData.EMPTY);
                observed.addProperty("nativeGetterEmpty", view.getModelData(here) == ModelData.EMPTY);
                var state = view.getBlockState(here); var model = mc.getBlockRenderer().getBlockModel(state);
                var prepared = model.getModelData(view, here, state, incoming);
                var contacts = prepared.get(design.inherent.skyweavecompat.client.GlassSurfaceContacts.PROPERTY);
                observed.addProperty("contactSnapshot", contacts != null);
                if (contacts != null) try {
                    observed.addProperty("contactFaces", ((java.util.List<?>) contacts.getClass().getMethod("faces").invoke(contacts)).size());
                } catch (NoSuchMethodException baseline) {}
                result.add(here.toShortString(), observed);
            }
            if (command.has("parallel") && command.get("parallel").getAsBoolean()) {
                var target = pos.offset(1, 0, 0);
                var expected = sample(view, target);
                if (expected.isEmpty()) throw new IllegalStateException("Parallel control requires emitted geometry");
                var views = new java.util.ArrayList<Object>();
                for (int i = 0; i < 4; i++) {
                    var workerView = sliceType.getConstructor(net.minecraft.client.multiplayer.ClientLevel.class).newInstance(mc.level);
                    sliceType.getMethod("copyData", context.getClass()).invoke(workerView, context); views.add(workerView);
                }
                var ready = new java.util.concurrent.CountDownLatch(4); var start = new java.util.concurrent.CountDownLatch(1);
                var workers = java.util.concurrent.Executors.newFixedThreadPool(4);
                try {
                    var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
                    for (var worker : views) results.add(workers.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Worker start timeout");
                        for (int i = 0; i < 4; i++) if (!sample((net.minecraft.world.level.BlockAndTintGetter) worker, target).equals(expected)) return false;
                        return !design.inherent.skyweavecompat.rendering.GeometryScope.sampling();
                    }));
                    if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not enter");
                    start.countDown();
                    for (var actual : results) if (!actual.get(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("Worker snapshot output/scope differs from serial");
                    result.addProperty("parallelWorkers", 4); result.addProperty("independentViews", views.size());
                    result.addProperty("matchingNativeSamples", 16);
                } finally {
                    start.countDown(); workers.shutdownNow();
                    if (!workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Worker cleanup timeout");
                    for (var worker : views) sliceType.getMethod("reset").invoke(worker);
                }
            }
            return result;
        } finally { sliceType.getMethod("reset").invoke(slice); }
    }

    private static JsonArray sample(net.minecraft.world.level.BlockAndTintGetter view, BlockPos pos) {
        var state = view.getBlockState(pos); var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(view, pos, state, view.getModelData(pos));
        long seed = state.getSeed(pos); var result = new JsonArray();
        for (var layer : model.getRenderTypes(state, RandomSource.create(seed), data))
            result.addAll(query(model, state, data, layer, RandomSource.create(seed), seed));
        return result;
    }

    static void confirmReopen(Minecraft mc) {
        String name = System.getProperty("skyweave_compat.probe.resources.world");
        if (name == null || !name.matches("resource-review-[a-z0-9-]{1,48}")
                || !Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(name).resolve(".resource-review-world"))
                || !(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen))
            throw new IllegalStateException("Expected the owned native reopen confirmation");
        for (var child : mc.screen.children()) if (child instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("selectWorld.backupJoinSkipButton")) { button.onPress(); return; }
        throw new IllegalStateException("Native proceed button unavailable");
    }

    private static JsonArray query(net.minecraft.client.resources.model.BakedModel model, BlockState state, ModelData data,
            net.minecraft.client.renderer.RenderType layer, RandomSource random, long seed) {
        var result = new JsonArray();
        for (int i = -1; i < 6; i++) {
            random.setSeed(seed);
            for (var q : model.getQuads(state, i < 0 ? null : Direction.values()[i], random, data, layer)) {
                if (result.size() > 4096) throw new IllegalStateException("Phase quad budget exceeded");
                var value = new JsonObject(); value.addProperty("cullGroup", i); value.addProperty("face", q.getDirection().getName());
                value.addProperty("sprite", q.getSprite().contents().name().toString());
                value.addProperty("tint", q.getTintIndex()); value.addProperty("shade", q.isShade());
                value.addProperty("ao", q.hasAmbientOcclusion()); value.add("vertices", new Gson().toJsonTree(q.getVertices()));
                result.add(value);
            }
        }
        return result;
    }

    private static AutoCloseable sourceScope() throws Exception {
        try {
            return (AutoCloseable) Class.forName("design.inherent.skyweavecompat.rendering.GeometryScope").getMethod("source").invoke(null);
        } catch (ClassNotFoundException baseline) {
            // Matched published baseline only; no synthetic replacement for its actual session cache.
            var field = Class.forName("design.inherent.skyweavecompat.client.GlassSurfaceContacts").getDeclaredField("SAMPLING");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var value = (ThreadLocal<Boolean>) field.get(null);
            Boolean previous = value.get(); value.set(true);
            return () -> { if (previous == null) value.remove(); else value.set(previous); };
        }
    }
}
