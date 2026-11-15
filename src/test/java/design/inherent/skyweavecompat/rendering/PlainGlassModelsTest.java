package design.inherent.skyweavecompat.rendering;

import design.inherent.skyweavecompat.client.PlainGlassModels;
import design.inherent.skyweavecompat.client.GlassBackfaces;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Set;
import design.inherent.skyweavecompat.Feature;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlainGlassModelsTest {
    private static boolean target(ResourceLocation id, Set<Feature> active) {
        if (GlassBackfaces.vanillaPane(id)) return false; // Native late modifier owns this boundary.
        for (String file : java.util.List.of("vanilla_glass", "vanilla_panes", "vanilla_tinted", "create_glass", "create_tiled",
                "rechiseled_pillars", "macaw_mosaic", "caramel_bricks", "bubble_gum", "ornate_grate", "industrial_grating")) {
            try (var stream = PlainGlassModelsTest.class.getResourceAsStream("/assets/skyweave_compat/transparency/" + file + ".json")) {
                assertNotNull(stream);
                var profile = TransparencyProfile.parse(com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                if (profile.enabled() && active.contains(profile.feature()) && profile.providers().contains(TransparencyProfile.Provider.PLAIN)
                        && profile.targets().stream().anyMatch(selector -> selector.family().matches(id.getNamespace(), id.getPath(), ignored -> false))) return true;
            } catch (java.io.IOException invalid) { throw new AssertionError(invalid); }
        }
        return false;
    }
    @Test void vanillaPaneVariantsWrapAfterCompositionWithOneOwnerAndNoPartQueries() {
        var source = (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> { throw new AssertionError("Registration must retain the complete upstream model"); });
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        for (String id : java.util.List.of("minecraft:glass_pane", "minecraft:red_stained_glass_pane",
                "minecraft:magenta_stained_glass_pane"))
            for (String variant : java.util.List.of("north=true,east=false", "north=false,east=true", "inventory"))
                models.put(new ModelResourceLocation(ResourceLocation.parse(id), variant), source);
        var event = new ModelEvent.ModifyBakingResult(models, null, null);
        PlainGlassModels.wrap(event, id -> target(id, Set.of()));
        assertTrue(models.values().stream().allMatch(model -> model == source));
        PlainGlassModels.wrap(event, id -> target(id, Set.of(Feature.FUSION_BACKFACES)));
        assertTrue(models.values().stream().allMatch(model -> model == source), "Pane wrapping must wait for Fusion's later modifier pass");
        PlainGlassModels.wrap(models, GlassBackfaces::vanillaPane);
        var once = new HashMap<>(models);
        for (var entry : models.entrySet()) {
            if (entry.getKey().getVariant().equals("inventory")) assertSame(source, entry.getValue());
            else assertInstanceOf(PlainGlassModels.class, entry.getValue());
        }
        PlainGlassModels.wrap(models, GlassBackfaces::vanillaPane);
        once.forEach((id, model) -> assertSame(model, models.get(id), "Repeated registration must not duplicate backfaces"));
    }

    @Test void wrapsOnlyTiledBlockStatesIncludingMultipartStatesAndRetainsOtherOwners() {
        var source = (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> { throw new AssertionError("Bake registration must not query models"); });
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        var block = new ModelResourceLocation(ResourceLocation.parse("create:tiled_glass"), "");
        var pane = new ModelResourceLocation(ResourceLocation.parse("create:tiled_glass_pane"), "north=true,east=false");
        var item = new ModelResourceLocation(ResourceLocation.parse("create:tiled_glass"), "inventory");
        var other = new ModelResourceLocation(ResourceLocation.parse("other:tiled_glass"), "");
        var ct = new ModelResourceLocation(ResourceLocation.parse("create:framed_glass"), "");
        for (var id : java.util.List.of(block, pane, item, other, ct)) models.put(id, source);
        var event = new ModelEvent.ModifyBakingResult(models, null, null);
        PlainGlassModels.wrap(event, id -> target(id, Set.of(Feature.CREATE_BACKFACES)));
        assertInstanceOf(PlainGlassModels.class, models.get(block));
        assertInstanceOf(PlainGlassModels.class, models.get(pane));
        for (var id : java.util.List.of(item, other, ct)) assertSame(source, models.get(id));
        var wrapped = models.get(pane);
        PlainGlassModels.wrap(event, id -> target(id, Set.of(Feature.CREATE_BACKFACES)));
        assertSame(wrapped, models.get(pane));
    }
    @Test void admitsMosaicStatesAndTintedGlassWithoutChangingOtherWindowSemantics() {
        var source = (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> { throw new AssertionError("Registration must not query models"); });
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        for (String part : java.util.List.of("base", "top", "middle", "bottom"))
            models.put(new ModelResourceLocation(ResourceLocation.parse("mcwwindows:red_mosaic_glass"), "part=" + part), source);
        var pane = new ModelResourceLocation(ResourceLocation.parse("mcwwindows:red_mosaic_glass_pane"), "north=true");
        var tinted = new ModelResourceLocation(ResourceLocation.parse("minecraft:tinted_glass"), "");
        models.put(pane, source); models.put(tinted, source);
        var controls = java.util.List.of("mcwwindows:one_way_glass", "mcwwindows:one_way_glass_pane",
                "mcwwindows:oak_window", "other:red_mosaic_glass");
        for (String id : controls) models.put(new ModelResourceLocation(ResourceLocation.parse(id), ""), source);
        var item = new ModelResourceLocation(ResourceLocation.parse("mcwwindows:red_mosaic_glass"), "inventory");
        models.put(item, source);
        var event = new ModelEvent.ModifyBakingResult(models, null, null);
        PlainGlassModels.wrap(event, id -> target(id, Set.of(Feature.FUSION_BACKFACES)));
        assertInstanceOf(PlainGlassModels.class, models.get(tinted));
        assertSame(source, models.get(pane), "Macaw selection stays independent of Fusion");
        PlainGlassModels.wrap(event, id -> target(id, Set.of(Feature.MACAW_GLASS)));
        assertInstanceOf(PlainGlassModels.class, models.get(pane));
        for (String part : java.util.List.of("base", "top", "middle", "bottom"))
            assertInstanceOf(PlainGlassModels.class, models.get(new ModelResourceLocation(
                    ResourceLocation.parse("mcwwindows:red_mosaic_glass"), "part=" + part)));
        for (String id : controls) assertSame(source, models.get(new ModelResourceLocation(ResourceLocation.parse(id), "")));
        assertSame(source, models.get(item));
    }

    @Test void approvedFamiliesAreIndependentAndExcludeScaffoldingAndUnapprovedVariants() {
        var targets = java.util.Map.of(
                Feature.CONFECTIONERY_BACKFACES, java.util.List.of("create_confectionery:caramel_bricks",
                        "create_confectionery:caramel_bricks_slab", "create_confectionery:caramel_bricks_stairs"),
                Feature.BUBBLE_GUM_BACKFACES, java.util.List.of("linearbearing:bubble_gum_block"),
                Feature.ORNATE_GRATE_BACKFACES, java.util.List.of("dndecor:ornate_grate"),
                Feature.INDUSTRIAL_GRATING_BACKFACES, java.util.List.of("bits_n_bobs:industrial_grating"));
        for (var entry : targets.entrySet()) for (String id : entry.getValue()) {
            assertTrue(target(ResourceLocation.parse(id), Set.of(entry.getKey())));
            var disabled = new java.util.HashSet<>(PlainGlassModels.FEATURES);
            disabled.remove(entry.getKey());
            assertFalse(target(ResourceLocation.parse(id), disabled));
        }
        for (String id : java.util.List.of("minecraft:scaffolding", "create:andesite_scaffolding",
                "create:brass_scaffolding", "create:copper_scaffolding", "create_confectionery:caramel",
                "bits_n_bobs:industrial_grating_panel", "dndecor:ornate_iron_glass"))
            assertFalse(target(ResourceLocation.parse(id), PlainGlassModels.FEATURES));
    }

}
