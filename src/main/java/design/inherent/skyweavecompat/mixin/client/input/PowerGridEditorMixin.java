package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.PowerGridEditor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen", remap = false)
abstract class PowerGridEditorMixin implements PowerGridEditor {}
