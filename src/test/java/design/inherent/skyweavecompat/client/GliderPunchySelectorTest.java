package design.inherent.skyweavecompat.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.selectors.*;

class GliderPunchySelectorTest {
    @Test void requiresOriginalHelperAndExactFinalCancellationShape() {
        var selector=GliderPunchySelector.parse("cancel",null);var owner=new ClassNode();owner.name="net/minecraft/client/renderer/ItemInHandRenderer";
        var method=new MethodNode(2,"original$punchy$cancelVanillaArms","(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,null);
        var origin=new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");origin.visit("mixin","punchy.mixin.client.ItemInHandRendererMixin");method.invisibleAnnotations=new java.util.ArrayList<>(java.util.List.of(origin));
        for(int i=0;i<3;i++){method.instructions.add(new VarInsnNode(Opcodes.ALOAD,11));method.instructions.add(cancel());}
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        assertEquals(MatchResult.EXACT_MATCH,selector.match(ElementNode.of(owner,method)));
        method.name="handler$zcd000$punchy$cancelVanillaArms";assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));method.name="original$punchy$cancelVanillaArms";
        owner.name="foreign/ItemInHandRenderer";assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));owner.name="net/minecraft/client/renderer/ItemInHandRenderer";
        origin.values.set(1,"foreign.mixin.ItemInHandRendererMixin");assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));origin.values.set(1,"punchy.mixin.client.ItemInHandRendererMixin");
        method.instructions.insertBefore(method.instructions.getLast(),new InsnNode(Opcodes.NOP));assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));method.instructions.remove(method.instructions.getLast().getPrevious());
        method.instructions.insertBefore(method.instructions.getFirst(),cancel());assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));method.instructions.remove(method.instructions.getFirst());
        ((VarInsnNode)method.instructions.getLast().getPrevious().getPrevious()).var=10;assertEquals(MatchResult.NONE,selector.match(ElementNode.of(owner,method)));
        assertThrows(IllegalArgumentException.class,()->GliderPunchySelector.parse("other",null));assertEquals(1,selector.getMinMatchCount());assertEquals(1,selector.getMaxMatchCount());
    }
    private static MethodInsnNode cancel(){return new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"org/spongepowered/asm/mixin/injection/callback/CallbackInfo","cancel","()V",false);}
}
