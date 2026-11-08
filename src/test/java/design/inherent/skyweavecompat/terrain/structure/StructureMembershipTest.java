package design.inherent.skyweavecompat.terrain.structure;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureMembershipTest {
    @Test void foreignMembershipRequiresAnExplicitTagAndSupportedBridge() {
        assertNull(StructureMembership.select(null, Set.of(), true));
        assertNull(StructureMembership.select(null, Set.of(StructureFamily.VILLAGES), false));
        assertSame(StructureFamily.VILLAGES, StructureMembership.select(null, Set.of(StructureFamily.VILLAGES), true));
        assertSame(StructureFamily.PORTALS, StructureMembership.select(StructureFamily.PORTALS, Set.of(), false));
        assertThrows(IllegalArgumentException.class, () -> StructureMembership.select(StructureFamily.PORTALS, Set.of(StructureFamily.VILLAGES), true));
        assertThrows(IllegalArgumentException.class, () -> StructureMembership.select(null, Set.of(StructureFamily.OUTPOSTS, StructureFamily.VILLAGES), true));
    }
    @Test void mixedProviderSetsShareOneFamilyButUnknownMembersKeepTheSetUnmanaged() {
        var members = Map.of("minecraft:village_plains", StructureFamily.VILLAGES,
                "ctov:small/village_plains", StructureFamily.VILLAGES,
                "towns_and_towers:badlands_pueblo", StructureFamily.VILLAGES);
        assertSame(StructureFamily.VILLAGES, StructureFamily.forSet(members.keySet(), members::get));
        assertNull(StructureFamily.forSet(List.of("ctov:small/village_plains", "foreign:unknown"), members::get));
        assertNull(StructureFamily.forSet(List.of("ctov:small/village_plains", "minecraft:pillager_outpost"),
                id -> members.getOrDefault(id, StructureFamily.forId(id))));
    }
}
