/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.client;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.selectors.*;

/** Select the pinned default hand cancellation while retaining earlier UI and action policy. */
@ITargetSelectorDynamic.SelectorId(namespace="skyweave",value="gliderpunchy")
public final class GliderPunchySelector implements ITargetSelectorDynamic {
    private static final String OWNER="net/minecraft/client/renderer/ItemInHandRenderer";
    private static final String DESC="(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private GliderPunchySelector() {}
    public static GliderPunchySelector parse(String name,ISelectorContext context) {
        if(!name.equals("cancel"))throw new IllegalArgumentException("Unknown pinned Punchy glider callback: "+name);
        return new GliderPunchySelector();
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
                || !method.name.equals("original$punchy$cancelVanillaArms")
                || !(origin(method.visibleAnnotations) || origin(method.invisibleAnnotations)))return MatchResult.NONE;
        int cancellations=0;MethodInsnNode last=null;
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call
                && call.getOpcode()==Opcodes.INVOKEVIRTUAL && call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")
                && call.name.equals("cancel") && call.desc.equals("()V")){cancellations++;last=call;}
        if(cancellations!=3)return MatchResult.NONE;
        var before=last.getPrevious();while(before!=null && before.getOpcode()<0)before=before.getPrevious();
        var after=last.getNext();while(after!=null && after.getOpcode()<0)after=after.getNext();
        return before instanceof VarInsnNode receiver && receiver.getOpcode()==Opcodes.ALOAD && receiver.var==11
                && after!=null && after.getOpcode()==Opcodes.RETURN?MatchResult.EXACT_MATCH:MatchResult.NONE;
    }
    private static boolean origin(java.util.List<AnnotationNode> annotations) {
        if(annotations==null)return false;
        for(var annotation:annotations){
            if(!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || annotation.values==null)continue;
            for(int i=0;i+1<annotation.values.size();i+=2)if(annotation.values.get(i).equals("mixin")
                    && annotation.values.get(i+1).equals("punchy.mixin.client.ItemInHandRendererMixin"))return true;
        }
        return false;
    }
}
