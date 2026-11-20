package design.inherent.skyweavecompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import punchy.config.PunchyConfig;

/** One native Punchy pose, with liquid before and glass after Iris's translucent world pass. */
public final class HandMaterials {
    private static final int MAX_QUADS = 512, MAX_MODELS = 32, MAX_DRAWS = 16;
    private static final Map<Item, String> LIQUIDS = Map.of(Items.POTION, "minecraft:item/liquid",
            Items.HONEY_BOTTLE, "minecraft:item/honey", Items.DRAGON_BREATH, "minecraft:item/dragon_liquid");
    private static final IdentityHashMap<BakedModel, Map<String, Optional<Partition>>> MODELS = new IdentityHashMap<>();
    private static final List<Draw> LATE = new ArrayList<>();
    private static final Set<Item> SPLIT_ITEMS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static ItemStack filteredStack;
    private static Object level;
    private static LocalPlayer player;
    private static boolean active, capturing, captured, replaying;
    private static long resourceRevision;
    private HandMaterials() {}

    private record Draw(ItemRenderer renderer, ItemStack stack, ItemDisplayContext display, boolean left,
                        Matrix4f pose, Matrix3f normal, int light, int overlay, BakedModel model,
                        ItemDrawScopes.Replay scope) {}
    private record Faces(List<BakedQuad> general, Map<Direction, List<BakedQuad>> sided) {
        List<BakedQuad> get(Direction direction) { return direction == null ? general : sided.get(direction); }
    }
    private record Partition(Faces liquid, Faces glass) {}

    public static void register(IEventBus bus) {
        bus.addListener((ModelEvent.BakingCompleted event) -> reset());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
            if (event.getLevel().isClientSide()) reset();
        });
    }
    public static void reset() { clear(); MODELS.clear(); resourceRevision = ClientResourceRevision.revision(); }
    public static void clear() {
        LATE.clear(); SPLIT_ITEMS.clear(); filteredStack = null; level = null; player = null;
        active = capturing = captured = replaying = false;
    }

    /** Called before Iris's own eligibility checks, including frames with no visible hands. */
    public static void begin(ItemInHandRenderer hands) {
        clear();
        if (resourceRevision != ClientResourceRevision.revision()) reset();
        var mc = Minecraft.getInstance();
        if (mc.player == null || !Iris.isPackInUseQuick() || !PunchyConfig.isModEnabled()) return;
        var cached = (design.inherent.skyweavecompat.mixin.client.materials.MaterialHandsAccess) hands;
        for (var hand : InteractionHand.values()) {
            var old = hand == InteractionHand.MAIN_HAND ? cached.skyweave$main() : cached.skyweave$off();
            var current = mc.player.getItemInHand(hand);
            if (eligible(old, hand)) SPLIT_ITEMS.add(old.getItem());
            if (eligible(current, hand)) SPLIT_ITEMS.add(current.getItem());
        }
        // A shared item in an unowned hand must keep native routing in both hands.
        for (var hand : InteractionHand.values()) if (PunchyConfig.isHandBlacklisted(mc.player, hand)) {
            SPLIT_ITEMS.remove(mc.player.getItemInHand(hand).getItem());
            SPLIT_ITEMS.remove((hand == InteractionHand.MAIN_HAND ? cached.skyweave$main() : cached.skyweave$off()).getItem());
        }
        active = !SPLIT_ITEMS.isEmpty();
        if (active) { player = mc.player; level = mc.level; }
    }
    private static boolean eligible(ItemStack stack, InteractionHand hand) {
        var mc = Minecraft.getInstance();
        if (!LIQUIDS.containsKey(stack.getItem()) || PunchyConfig.isHandBlacklisted(mc.player, hand)) return false;
        var model = mc.getItemRenderer().getItemModelShaper().getItemModel(stack.getItem());
        return partition(model, stack).isPresent();
    }
    public static boolean active() {
        var mc = Minecraft.getInstance();
        return active && player == mc.player && level == mc.level && resourceRevision == ClientResourceRevision.revision();
    }
    public static boolean split(ItemStack stack) {
        return active() && SPLIT_ITEMS.contains(stack.getItem());
    }
    public static boolean handPass() { return active() && HandRenderer.INSTANCE.isActive(); }

    /** The late pass reuses draw arguments; it must not advance native animation/physics a second time. */
    public static boolean latePose() {
        if (!active() || !HandRenderer.INSTANCE.isActive()) return false;
        if (!HandRenderer.INSTANCE.isRenderingSolid()) return captured;
        capturing = true;
        return false;
    }
    public static void poseFinished(boolean success) {
        if (!capturing) return;
        capturing = false;
        if (success) captured = true;
        else clear();
    }

    /** Return true only when a wholly translucent native item is deferred to its ordinary Iris pass. */
    public static boolean capture(ItemRenderer renderer, ItemStack stack, ItemDisplayContext display, boolean left,
                                  PoseStack pose, int light, int overlay, BakedModel model) {
        if (!active() || !capturing || replaying) return false;
        boolean split = split(stack) && partition(model, stack).isPresent();
        boolean translucent = !split && !LIQUIDS.containsKey(stack.getItem()) && HandRenderer.INSTANCE.isHandTranslucent(stack);
        if (split || translucent) {
            // Only final native item draws are retained, never model/animation traversal or vertex buffers.
            if (LATE.size() >= MAX_DRAWS) throw new IllegalStateException("Iris held-material draw limit exceeded");
            LATE.add(new Draw(renderer, stack.copy(), display, left, new Matrix4f(pose.last().pose()),
                    new Matrix3f(pose.last().normal()), light, overlay, model, ItemDrawScopes.capture(stack)));
        }
        return translucent;
    }
    public static void replay(MultiBufferSource buffers) {
        if (!active() || !captured || replaying) return;
        replaying = true;
        try {
            for (var draw : LATE) {
                var pose = new PoseStack();
                pose.last().pose().set(draw.pose); pose.last().normal().set(draw.normal);
                try (var scope = draw.scope == null ? null : draw.scope.open(draw.stack)) {
                    draw.renderer.render(draw.stack, draw.display, draw.left, pose, buffers, draw.light, draw.overlay, draw.model);
                }
            }
        } finally { replaying = false; }
    }

    /** Static, ordinary baked geometry only. Unknown/custom renderers retain their native rendering. */
    public static BakedModel layer(BakedModel model, ItemStack stack) {
        if (!split(stack) || stack != filteredStack || !HandRenderer.INSTANCE.isActive()) return model;
        var partition = partition(model, stack);
        if (partition.isEmpty()) return model;
        Faces faces = HandRenderer.INSTANCE.isRenderingSolid() ? partition.get().liquid : partition.get().glass;
        return new net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel>(model) {
            @Override public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,
                                                      Direction face, RandomSource random) { return faces.get(face); }
        };
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
    public static Scope drawing(ItemStack stack, BakedModel model) {
        ItemStack previous = filteredStack;
        filteredStack = split(stack) && partition(model, stack).isPresent() ? stack : null;
        return () -> filteredStack = previous;
    }
    private static Optional<Partition> partition(BakedModel model, ItemStack stack) {
        String liquid = LIQUIDS.get(stack.getItem());
        if (liquid == null || !(model instanceof SimpleBakedModel) || model.isCustomRenderer()) return Optional.empty();
        if (MODELS.size() >= MAX_MODELS && !MODELS.containsKey(model)) MODELS.clear();
        return MODELS.computeIfAbsent(model, ignored -> new HashMap<>()).computeIfAbsent(liquid, ignored -> inspect(model, liquid));
    }
    private static Optional<Partition> inspect(BakedModel model, String liquid) {
        var liquids = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        var glass = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        List<BakedQuad> generalLiquid = List.of(), generalGlass = List.of();
        var faces = new ArrayList<Direction>(); faces.add(null); faces.addAll(List.of(Direction.values()));
        int count = 0, liquidCount = 0, glassCount = 0;
        for (var face : faces) {
            var early = new ArrayList<BakedQuad>(); var late = new ArrayList<BakedQuad>();
            for (var quad : model.getQuads(null, face, RandomSource.create(42))) {
                if (++count > MAX_QUADS) return Optional.empty();
                if (quad.getSprite().contents().name().toString().equals(liquid)) { early.add(quad); liquidCount++; }
                else { late.add(quad); glassCount++; }
            }
            if (face == null) { generalLiquid = List.copyOf(early); generalGlass = List.copyOf(late); }
            else { liquids.put(face, List.copyOf(early)); glass.put(face, List.copyOf(late)); }
        }
        return liquidCount == 0 || glassCount == 0 ? Optional.empty() : Optional.of(new Partition(
                new Faces(generalLiquid, Map.copyOf(liquids)), new Faces(generalGlass, Map.copyOf(glass))));
    }
}
