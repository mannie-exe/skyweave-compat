package design.inherent.skyweavecompat.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import timmychips.modefiteitemdefinitions.ModefiteClientCommon;
import timmychips.modefiteitemdefinitions.property.registry.*;
import timmychips.modefiteitemdefinitions.property.type.ItemModelTypes;
import timmychips.modefiteitemdefinitions.property.type.codec.*;

/** Client-only, reload-owned programs. All geometry still uses native baking and leaf rendering. */
public final class ItemDefinitions {
    // Source inventory is below 102 KiB per root. Limits include headroom, not an unbounded pack importer.
    private static final int MAX_ROOTS = 4096, MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final ThreadLocal<Prepared> PREPARING = new ThreadLocal<>();
    private static final ThreadLocal<Invocation> INVOCATION = new ThreadLocal<>();
    private static final ThreadLocal<Set<ResourceLocation>> LEAVES = new ThreadLocal<>();
    private static final ThreadLocal<Colors> COLORS = new ThreadLocal<>();
    private static volatile Epoch active;
    private static volatile long session;
    private static long revision;
    private ItemDefinitions() {}

    public record Source(ResourceLocation resource, String pack) {}
    public record Refusal(ResourceLocation item, Source source, String reason) {}
    public record Prepared(Map<ResourceLocation, ResourceDefinitions.Root<ResourceComponents.Input>> roots,
                           Map<ResourceLocation, Source> sources, Set<ResourceLocation> dependencies,
                           List<Refusal> refusals, ResourceValueCache cache, long nanos) {
        public Prepared {
            roots = Map.copyOf(roots); sources = Map.copyOf(sources);
            dependencies = Set.copyOf(dependencies); refusals = List.copyOf(refusals);
        }
    }
    private record Epoch(Prepared prepared, Map<String, BakedModel> models, BakedModel missing, long revision) {}
    private record Invocation(ItemStack stack, LivingEntity entity, Level level, int seed) {}
    private record Colors(ItemStack stack, int[] values) {}

    public static void register() {
        design.inherent.skyweavecompat.client.ItemDrawScopes.register(ItemDefinitions::captureLeaf);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> clearProjections());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
            if (event.getLevel().isClientSide()) clearProjections();
        });
    }
    private static void clearProjections() { session++; var epoch = active; if (epoch != null) epoch.prepared.cache.clear(); }

    /** A captured native leaf must not select its root again or lose the exact leaf's tint scope. */
    private static design.inherent.skyweavecompat.client.ItemDrawScopes.Replay captureLeaf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !bypassed(id)) return null;
        var colors = COLORS.get();
        int[] saved = colors != null && colors.stack == stack ? colors.values.clone() : null;
        return replay -> {
            var previous = COLORS.get();
            var leaf = leafScope(id);
            if (saved == null) COLORS.remove(); else COLORS.set(new Colors(replay, saved));
            return () -> { restore(COLORS, previous); leaf.close(); };
        };
    }

    public static void prepare(ResourceManager resources) {
        PREPARING.remove();
        long start = System.nanoTime();
        var winners = new TreeMap<ResourceLocation, Map.Entry<ResourceLocation, Resource>>();
        for (String folder : List.of("items", "modefite_items_override"))
            resources.listResources(folder, id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
                String path = id.getPath().substring(folder.length() + 1, id.getPath().length() - 5);
                var item = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
                if (BuiltInRegistries.ITEM.containsKey(item)) winners.put(item, Map.entry(id, resource));
            });
        if (winners.size() > MAX_ROOTS) throw new IllegalStateException("Modefite definitions exceed total root limit");
        var cache = new ResourceValueCache(1024);
        var values = new NativeResourceValues(cache);
        var roots = new LinkedHashMap<ResourceLocation, ResourceDefinitions.Root<ResourceComponents.Input>>();
        var sources = new LinkedHashMap<ResourceLocation, Source>();
        var dependencies = new LinkedHashSet<ResourceLocation>();
        var refusals = new ArrayList<Refusal>();
        long[] bytes = {0};
        for (var entry : winners.entrySet()) {
            var resource = entry.getValue().getValue();
            var source = new Source(entry.getValue().getKey(), resource.sourcePackId());
            try (var input = resource.open()) {
                var bounded = new java.io.FilterInputStream(input) {
                    @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
                        int n = super.read(b, off, len); if (n > 0) bytes[0] += n; return n;
                    }
                    @Override public int read() throws java.io.IOException {
                        int n = super.read(); if (n >= 0) bytes[0]++; return n;
                    }
                };
                var root = ResourceDefinitions.compile(ResourceJson.read(bounded), values);
                roots.put(entry.getKey(), root); sources.put(entry.getKey(), source);
                for (String id : root.dependencies()) if (!id.equals(ResourceDefinitions.MISSING)) dependencies.add(ResourceLocation.parse(id));
            } catch (Exception failure) {
                String reason = Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName());
                var refusal = new Refusal(entry.getKey(), source, reason); refusals.add(refusal);
                LogUtils.getLogger().warn("Item definition {} from {} ({}) refused: {}", entry.getKey(), source.pack, source.resource, reason);
            }
            if (bytes[0] > MAX_TOTAL_BYTES) throw new IllegalStateException("Modefite definitions exceed total byte limit; previous epoch retained");
        }
        var prepared = new Prepared(roots, sources, dependencies, refusals, cache, System.nanoTime() - start);
        PREPARING.set(prepared);
        ModefiteClientCommon.modelIds = prepared.dependencies;
    }

    public static Prepared takePreparation() { var value = PREPARING.get(); PREPARING.remove(); return value; }

    public static void bind(ModelEvent.BakingCompleted event) {
        var prepared = ((DefinitionBake) event.getModelBakery()).skyweave$definitions();
        if (prepared == null) throw new IllegalStateException("Native model bake has no matching item-definition preparation");
        var models = new HashMap<String, BakedModel>();
        var missing = event.getModelManager().getMissingModel();
        for (var id : prepared.dependencies) models.put(id.toString(), event.getModels().getOrDefault(new ModelResourceLocation(id, "standalone"), missing));
        models.put(ResourceDefinitions.MISSING, missing);
        var epoch = new Epoch(prepared, Map.copyOf(models), missing, ++revision);
        // Native root metadata is published with the actual successful bake, never during preparation.
        ItemModelTypes.Registry.clear();
        timmychips.modefiteitemdefinitions.property.resolver.ResolveRecursive.WARNED_MODELS.clear();
        RangePropertyRegistry.init(); ConditionPropertyRegistry.init(); SelectPropertyRegistry.init();
        prepared.roots.forEach((id, root) -> ItemModelTypes.Registry.putRoot(id,
                new ItemModelRootDefinition(new EmptyModelDefinition(EmptyModelDefinition.TYPE), root.handAnimation(), root.oversizedInGui(), root.swapScale())));
        active = epoch;
        LogUtils.getLogger().info("Item definitions epoch {}: {} roots, {} models, {} refusals, {} ms preparation",
                epoch.revision, prepared.roots.size(), models.size(), prepared.refusals.size(), prepared.nanos / 1_000_000);
    }

    /** Probe/Atlas views contain identifiers and values only, no mutable world or model references. */
    public static Map<ResourceLocation, Source> sources() { var epoch = active; return epoch == null ? Map.of() : epoch.prepared.sources; }
    public static List<Refusal> refusals() { var epoch = active; return epoch == null ? List.of() : epoch.prepared.refusals; }
    public static long revision() { var epoch = active; return epoch == null ? 0 : epoch.revision; }
    public static List<String> selectedModels(ResourceLocation id, ItemStack stack, LivingEntity entity, ItemDisplayContext display) {
        var epoch = active; var root = epoch == null ? null : epoch.prepared.roots.get(id);
        return root == null ? List.of() : root.layers(new ResourceComponents.Input(stack, entity, display)).stream().map(ResourceDefinitions.Layer::model).toList();
    }

    public static Optional<BakedModel> resolve(ResourceLocation id, ItemDisplayContext display, ItemStack stack, LivingEntity entity) {
        var epoch = active;
        if (id == null || bypassed(id) || epoch == null) return Optional.empty();
        var root = epoch.prepared.roots.get(id);
        if (root == null) return Optional.empty();
        var layers = root.layers(new ResourceComponents.Input(stack, entity, display));
        var first = layers.isEmpty() ? epoch.missing : epoch.models.getOrDefault(layers.getFirst().model(), epoch.missing);
        var call = INVOCATION.get();
        boolean scoped = call != null && call.stack == stack;
        Level level = scoped ? call.level : entity == null ? Minecraft.getInstance().level : entity.level();
        int seed = scoped ? call.seed : 0;
        try (var leaf = leafScope(id)) { first = overridden(epoch, first, stack, level, entity, seed); }
        // GUI callers inspect these flags before ItemRenderer.render; expose the resolved leaf's metadata.
        return Optional.of(new View(first, entity, level, seed, id, epoch.revision, session));
    }

    /** Scoped around the overload with native entity/world/seed, including Modefite's early interception. */
    public interface Scope extends AutoCloseable { @Override void close(); }
    public static Scope invocation(ItemStack stack, LivingEntity entity, Level level, int seed) {
        var previous = INVOCATION.get(); INVOCATION.set(new Invocation(stack, entity, level, seed));
        return () -> restore(INVOCATION, previous);
    }

    public static boolean render(ItemRenderer renderer, ItemStack stack, ItemDisplayContext display, boolean left,
                                 PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel supplied) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var epoch = active;
        var root = epoch == null || id == null ? null : epoch.prepared.roots.get(id);
        if (root == null || bypassed(id)) return false;
        var call = INVOCATION.get();
        boolean scoped = call != null && call.stack == stack;
        View view = supplied instanceof View candidate && candidate.session == session && candidate.item.equals(id) ? candidate : null;
        LivingEntity entity = scoped ? call.entity : view == null ? null : view.entity.get();
        Level level = scoped ? call.level : view == null ? Minecraft.getInstance().level : view.level.get();
        int seed = scoped ? call.seed : view == null ? 0 : view.seed;
        var context = new ResourceComponents.Input(stack, entity, display);
        var previousColors = COLORS.get();
        try (var leaf = leafScope(id)) {
            for (var layer : root.layers(context)) {
                var model = epoch.models.getOrDefault(layer.model(), epoch.missing);
                model = overridden(epoch, model, stack, level, entity, seed);
                int[] tints = new int[layer.tints().size()];
                for (int index = 0; index < tints.length; index++) tints[index] = layer.tints().get(index).applyAsInt(context);
                COLORS.set(new Colors(stack, tints));
                renderer.render(stack, display, left, pose, buffers, light, overlay, model);
            }
        } finally {
            restore(COLORS, previousColors);
        }
        return true;
    }

    /** Null delegates legacy/native color. Modern model tint lists own only their current leaf invocation. */
    public static Integer color(ItemStack stack, int index) {
        var colors = COLORS.get();
        return colors == null || colors.stack != stack ? null : index >= 0 && index < colors.values.length ? colors.values[index] : -1;
    }
    /** A caller can retain a model view across reload. Re-enter native lookup after its epoch or item disappears. */
    public static BakedModel currentModel(ItemRenderer renderer, ItemStack stack, BakedModel supplied) {
        if (!(supplied instanceof View view)) return supplied;
        var epoch = active;
        if (epoch != null && view.revision == epoch.revision && view.session == session
                && view.item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return supplied;
        var call = INVOCATION.get();
        return renderer.getModel(stack, call == null ? Minecraft.getInstance().level : call.level,
                call == null ? null : call.entity, call == null ? 0 : call.seed);
    }
    private static <T> void restore(ThreadLocal<T> local, T previous) { if (previous == null) local.remove(); else local.set(previous); }
    private static Scope leafScope(ResourceLocation id) {
        var leaves = LEAVES.get();
        if (leaves == null) { leaves = new HashSet<>(); LEAVES.set(leaves); }
        var entered = leaves; entered.add(id);
        return () -> { entered.remove(id); if (entered.isEmpty()) LEAVES.remove(); };
    }
    private static BakedModel overridden(Epoch epoch, BakedModel model, ItemStack stack, Level level, LivingEntity entity, int seed) {
        var result = model.getOverrides().resolve(model, stack, level instanceof ClientLevel clientLevel ? clientLevel : null, entity, seed);
        return result == null ? epoch.missing : result;
    }
    private static boolean bypassed(ResourceLocation id) { var leaves = LEAVES.get(); return leaves != null && leaves.contains(id); }
    private static final class View extends BakedModelWrapper<BakedModel> {
        final java.lang.ref.WeakReference<LivingEntity> entity;
        final java.lang.ref.WeakReference<Level> level;
        final ResourceLocation item;
        final long revision, session;
        final int seed;
        View(BakedModel model, LivingEntity entity, Level level, int seed, ResourceLocation item, long revision, long session) {
            super(model); this.entity = new java.lang.ref.WeakReference<>(entity); this.level = new java.lang.ref.WeakReference<>(level);
            this.seed = seed; this.item = item; this.revision = revision; this.session = session;
        }
    }
}
