package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla recipe search is not part of the screen's ordinary focused-child path. */
@Mixin(RecipeBookComponent.class)
public interface RecipeSearchAccess {
    @Accessor("searchBox") EditBox skyweave$searchBox();
}
