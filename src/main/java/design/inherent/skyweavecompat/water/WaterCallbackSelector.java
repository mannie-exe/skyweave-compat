package design.inherent.skyweavecompat.water;

import org.spongepowered.asm.mixin.injection.selectors.*;

/** A single required DW handler; ordinary regex selectors deliberately exclude merged methods. */
@ITargetSelectorDynamic.SelectorId(namespace = "skyweave", value = "watercallback")
public final class WaterCallbackSelector implements ITargetSelectorDynamic {
    private final ITargetSelector delegate;
    private WaterCallbackSelector(String name) {
        delegate = MemberMatcher.parse("/.*dynamicwaters\\$" + name + "/", null);
    }
    public static WaterCallbackSelector parse(String name, ISelectorContext context) {
        if (!name.equals("repairRiverAfterCarvers"))
            throw new IllegalArgumentException("Unknown pinned native water callback: " + name);
        return new WaterCallbackSelector(name);
    }
    @Override public ITargetSelector next() { return null; }
    @Override public ITargetSelector configure(Configure request, String... args) { request.checkArgs(args); return this; }
    @Override public ITargetSelector validate() { delegate.validate(); return this; }
    @Override public ITargetSelector attach(ISelectorContext context) { return this; }
    @Override public int getMinMatchCount() { return 1; }
    @Override public int getMaxMatchCount() { return 1; }
    @Override public <TNode> MatchResult match(ElementNode<TNode> node) { return delegate.match(node); }
}
