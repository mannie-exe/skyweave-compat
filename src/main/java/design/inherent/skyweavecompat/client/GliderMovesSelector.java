/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.client;

import org.objectweb.asm.tree.AnnotationNode;
import org.spongepowered.asm.mixin.injection.selectors.*;

/** The exact merged Moves wrapper, without an unrelated optional mod's bundled selector library. */
@ITargetSelectorDynamic.SelectorId(namespace="skyweave",value="glidermoves")
public final class GliderMovesSelector implements ITargetSelectorDynamic {
    private static final String OWNER="net/minecraft/client/renderer/entity/layers/ItemInHandLayer";
    private static final String SOURCE="punchymoves.mixin.client.ItemInHandLayerMixin";
    private static final String DESC="(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V";
    private GliderMovesSelector() {}
    public static GliderMovesSelector parse(String name,ISelectorContext context) {
        if(!name.equals("item"))throw new IllegalArgumentException("Unknown pinned Moves glider callback: "+name);
        return new GliderMovesSelector();
    }
    @Override public ITargetSelector next(){return null;}
    @Override public ITargetSelector configure(Configure request,String...args){request.checkArgs(args);return this;}
    @Override public ITargetSelector validate(){return this;}
    @Override public ITargetSelector attach(ISelectorContext context){return this;}
    @Override public int getMinMatchCount(){return 1;}
    @Override public int getMaxMatchCount(){return 1;}
    @Override public <TNode> MatchResult match(ElementNode<TNode> node) {
        var method=node.getMethod();
        if(!OWNER.equals(node.getOwner()) || method==null || !DESC.equals(method.desc)
                || !method.name.endsWith("$punchymoves$item"))return MatchResult.NONE;
        return origin(method.visibleAnnotations) || origin(method.invisibleAnnotations)?MatchResult.EXACT_MATCH:MatchResult.NONE;
    }
    private static boolean origin(java.util.List<AnnotationNode> annotations) {
        if(annotations==null)return false;
        for(var annotation:annotations) {
            if(!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || annotation.values==null)continue;
            for(int i=0;i+1<annotation.values.size();i+=2)
                if(annotation.values.get(i).equals("mixin") && SOURCE.equals(annotation.values.get(i+1)))return true;
        }
        return false;
    }
}
