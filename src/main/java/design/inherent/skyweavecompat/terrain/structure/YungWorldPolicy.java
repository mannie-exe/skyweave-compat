package design.inherent.skyweavecompat.terrain.structure;

import java.util.Map;

/** The marker is verified before ServerLevels exist; the generator owns the shared snapshot. */
public interface YungWorldPolicy {
    Map<String, Boolean> skyweave$yungReplacements();
}
