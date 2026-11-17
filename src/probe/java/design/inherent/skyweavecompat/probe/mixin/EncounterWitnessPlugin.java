package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Select fixture observers without resolving optional target classes in other probe profiles. */
public final class EncounterWitnessPlugin implements IMixinConfigPlugin {
    @Override public List<String> getMixins() {
        if(!Boolean.getBoolean("neighbors.probe.encounters"))return List.of();
        var result=new ArrayList<String>();result.add("EncounterSpawnerWitnessMixin");
        if(OptionalIntegrations.loaded("variantsandventures"))result.add("MurkGoalWitnessMixin");
        if(OptionalIntegrations.loaded("dynamicwaters"))result.add("EncounterWatershedWitnessMixin");
        if(OptionalIntegrations.enabled(Feature.VARIANT_TRIAL_ALIASES))result.add("TrialAliasWitnessMixin");
        return List.copyOf(result);
    }
    @Override public void onLoad(String name){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){return true;}
    @Override public void acceptTargets(Set<String> mine,Set<String> other){}
    @Override public void preApply(String name,ClassNode target,String mixin,IMixinInfo info){}
    @Override public void postApply(String name,ClassNode target,String mixin,IMixinInfo info){}
}
