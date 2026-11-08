package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.climate.EnvironmentHeight;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.GenerationProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** A separate effects provider avoids changing the shared vanilla Overworld singleton. */
public final class IslandSky extends DimensionSpecialEffects.OverworldEffects {
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.parse(GenerationProfile.ID), new IslandSky());
    }
    @Override public float getCloudHeight() {
        var level = Minecraft.getInstance().level;
        return level != null && IslandClimate.applies(level)
                ? EnvironmentHeight.cloudHeight(super.getCloudHeight(), level.getMinBuildHeight(), level.getHeight())
                : super.getCloudHeight();
    }
}
