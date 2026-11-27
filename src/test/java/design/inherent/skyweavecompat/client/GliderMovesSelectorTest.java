package design.inherent.skyweavecompat.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.selectors.*;

class GliderMovesSelectorTest {
    private static final String DESC="(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V";
    @Test void requiresExactTargetDescriptorAndMergedProviderOrigin() {
        var selector=GliderMovesSelector.parse("item",null);var owner=new ClassNode();owner.name="net/minecraft/client/renderer/entity/layers/ItemInHandLayer";
        var method=new MethodNode(2,"wrapOperation$zek000$punchy_moves$punchymoves$item",DESC,null,null);
        assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));
        var annotation=new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");annotation.visit("mixin","punchymoves.mixin.client.ItemInHandLayerMixin");method.invisibleAnnotations=new java.util.ArrayList<>(java.util.List.of(annotation));
        assertEquals(MatchResult.EXACT_MATCH,selector.match(ElementNode.of(owner,method)));
        owner.name="foreign/ItemInHandLayer";assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));owner.name="net/minecraft/client/renderer/entity/layers/ItemInHandLayer";
        method.desc="()V";assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));method.desc=DESC;
        method.name="wrapOperation$zek000$punchy_moves$punchymoves$other";assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));method.name="wrapOperation$zek000$punchy_moves$punchymoves$item";
        annotation.values.set(1,"foreign.mixin.ItemInHandLayerMixin");assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));
        assertEquals(1,selector.getMinMatchCount());assertEquals(1,selector.getMaxMatchCount());
        assertThrows(IllegalArgumentException.class,()->GliderMovesSelector.parse("other",null));
    }
}
