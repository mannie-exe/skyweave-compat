package design.inherent.skyweavecompat.bluemap;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import de.bluecolored.bluemap.api.BlueMapAPI;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

/** Loaded only on the dedicated server after an exact-version metadata gate. */
public final class BlueMapIntegration {
    private static final String SCRIPT = "skyweave-compat/viewer.js";
    private static final String BUNDLE = "assets/index-b72fc5a8.js";
    private static final String BUNDLE_HASH = "d6310afa1d0529fd78fa50c714f3422e6a7e574b3e5842557f3d2db9ecb9c056";
    private final Consumer<BlueMapAPI> enable = this::enable;
    private final Consumer<BlueMapAPI> disable = this::disable;
    private BlueMapAPI api;
    private Path verifiedWorld;
    private Path webRoot;
    private int minY, maxY;
    private boolean stopped;

    private BlueMapIntegration() {}
    public static void register() {
        var integration = new BlueMapIntegration();
        BlueMapAPI.onEnable(integration.enable);
        BlueMapAPI.onDisable(integration.disable);
        NeoForge.EVENT_BUS.addListener(integration::started);
        NeoForge.EVENT_BUS.addListener(integration::stopping);
    }

    // Minecraft access stays on the server event thread, not BlueMap's asynchronous callbacks.
    private synchronized void started(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        var generator = level.getChunkSource().getGenerator();
        try {
            if (generator instanceof SkyIslandChunkGenerator) {
                var root = server.getWorldPath(LevelResource.ROOT).toRealPath();
                boolean expanded = generator instanceof GeologicalGenerator geology && geology.skyweave$isEnabled();
                if (!expanded || Files.isRegularFile(root.resolve("skyweave-generation.json"))) {
                    verifiedWorld = root;
                    minY = level.getMinBuildHeight();
                    maxY = level.getMaxBuildHeight();
                }
            }
            publish();
        } catch (IOException | RuntimeException failure) { failClosed(failure); }
    }

    private synchronized void enable(BlueMapAPI instance) {
        if (stopped) return;
        api = instance;
        try {
            webRoot = instance.getWebApp().getWebRoot().toRealPath();
            if (!"5.7".equals(instance.getBlueMapVersion()) || !supportedWebapp(webRoot))
                throw new IOException("Unsupported BlueMap version or webapp; retaining the original viewer");
            try (var source = BlueMapIntegration.class.getResourceAsStream("/bluemap/viewer.js")) {
                if (source == null) throw new IOException("Missing packaged BlueMap viewer adapter");
                write(webRoot.resolve(SCRIPT), source.readAllBytes());
            }
            publish();
            instance.getWebApp().registerScript(SCRIPT);
        } catch (IOException | RuntimeException failure) { failClosed(failure); }
    }

    private synchronized void disable(BlueMapAPI instance) {
        if (api != instance) return;
        clearManifest(); api = null; webRoot = null;
    }

    private void stopping(ServerStoppingEvent event) {
        synchronized (this) {
            stopped = true;
            clearManifest(); api = null; verifiedWorld = null; webRoot = null;
        }
        // Do not hold our monitor while acquiring BlueMap's listener lock.
        BlueMapAPI.unregisterListener(enable);
        BlueMapAPI.unregisterListener(disable);
    }

    private void publish() throws IOException {
        if (api == null || webRoot == null) return;
        var settings = BlueMapSettings.load(design.inherent.skyweavecompat.configuration.ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get()));
        var selected = new TreeSet<String>();
        var bounds = new JsonObject();
        if (verifiedWorld != null) for (var map : api.getMaps()) {
            if (settings.maps().contains(map.getId()) && map.getWorld().getSaveFolder().toRealPath().equals(verifiedWorld)) {
                selected.add(map.getId());
                var height = new JsonObject(); height.addProperty("minY", minY); height.addProperty("maxY", maxY);
                bounds.add(map.getId(), height);
            }
        }
        var manifest = new Gson().toJsonTree(settings).getAsJsonObject();
        manifest.add("maps", new Gson().toJsonTree(selected));
        manifest.add("bounds", bounds);
        manifest.addProperty("schema", 2);
        manifest.addProperty("bundle", BUNDLE);
        writeManifest(manifest);
    }

    private void failClosed(Exception failure) {
        clearManifest(); api = null;
        LogUtils.getLogger().warn("Skyweave BlueMap adapter disabled: {}", failure.getMessage());
    }

    private void clearManifest() {
        if (webRoot == null) return;
        try {
            var empty = new JsonObject(); empty.addProperty("schema", 2); empty.add("maps", new JsonArray());
            writeManifest(empty);
        } catch (IOException failure) { LogUtils.getLogger().warn("Could not clear Skyweave BlueMap manifest", failure); }
    }

    private void writeManifest(JsonObject value) throws IOException {
        write(webRoot.resolve("skyweave-compat/settings.json"), (new Gson().toJson(value) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static boolean supportedWebapp(Path root) throws IOException {
        var bundle = root.resolve(BUNDLE);
        if (!Files.isRegularFile(bundle) || Files.size(bundle) != 1013395) return false;
        try { return BUNDLE_HASH.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(bundle)))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void write(Path path, byte[] contents) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path.getParent()) || Files.isSymbolicLink(path)) throw new IOException("Refusing symlink in adapter path");
        var temporary = Files.createTempFile(path.getParent(), ".adapter-", ".tmp");
        try { Files.write(temporary, contents); Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(temporary); }
    }
}
