package design.inherent.skyweavecompat.terrain.structure;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalStructureIdentityTest {
    private static final ResourceLocation ID = ResourceLocation.parse("ctov:small/village_jungle");
    private static CompoundTag piece(String extra) throws Exception {
        return TagParser.parseTag("{id:'minecraft:jigsaw',BB:[I;0,10,0,10,20,10],PosX:0,PosY:10,PosZ:0," + extra + "}");
    }
    @Test void compoundAndPieceOrderDoNotDistinguishAliases() throws Exception {
        var first = piece("rotation:'NONE',pool_element:{location:'ctov:house',processors:'ctov:house'}");
        var equivalent = piece("pool_element:{processors:'ctov:house',location:'ctov:house'},rotation:'NONE'");
        var second = piece("rotation:'NONE',pool_element:{location:'ctov:road'}");
        assertEquals(PhysicalStructureIdentity.of(ID, List.of(first, second)),
                PhysicalStructureIdentity.of(ID, List.of(second, equivalent)));
    }
    @Test void equalBoundsDoNotCollapseDifferentLayouts() throws Exception {
        var original = piece("rotation:'NONE',pool_element:{location:'ctov:house',processors:'ctov:house'}");
        for (var replacement : List.of(
                "rotation:'CLOCKWISE_90',pool_element:{location:'ctov:house',processors:'ctov:house'}",
                "rotation:'NONE',pool_element:{location:'ctov:smith',processors:'ctov:house'}",
                "rotation:'NONE',pool_element:{location:'ctov:house',processors:'ctov:ruin'}"))
            assertNotEquals(PhysicalStructureIdentity.of(ID, List.of(original)), PhysicalStructureIdentity.of(ID, List.of(piece(replacement))));
        var moved = original.copy(); moved.putInt("PosX", 1);
        assertNotEquals(PhysicalStructureIdentity.of(ID, List.of(original)), PhysicalStructureIdentity.of(ID, List.of(moved)));
        assertNotEquals(PhysicalStructureIdentity.of(ID, List.of(original)),
                PhysicalStructureIdentity.of(ResourceLocation.parse("other:village"), List.of(original)));
    }
    @Test void invalidOrExcessiveLayoutsFailExplicitly() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PhysicalStructureIdentity.of(ID, List.of()));
        assertThrows(IllegalArgumentException.class, () -> PhysicalStructureIdentity.of(ID,
                java.util.Collections.nCopies(4097, new CompoundTag())));
        var oversized = new CompoundTag(); oversized.putByteArray("oversized", new byte[8 * 1024 * 1024]);
        assertThrows(IllegalArgumentException.class, () -> PhysicalStructureIdentity.of(ID, List.of(oversized)));
    }
    @Test void derivedJigsawEnvelopeDoesNotDistinguishAssemblyFromReload() throws Exception {
        var assembled = piece("rotation:'NONE',pool_element:{location:'ctov:house'}");
        var persisted = assembled.copy(); persisted.putIntArray("BB",new int[]{0,10,0,10,14,10});
        assertEquals(PhysicalStructureIdentity.of(ID,List.of(assembled)),PhysicalStructureIdentity.of(ID,List.of(persisted)));
        assembled.putString("id","example:custom");persisted.putString("id","example:custom");
        assertNotEquals(PhysicalStructureIdentity.of(ID,List.of(assembled)),PhysicalStructureIdentity.of(ID,List.of(persisted)));
    }
    @Test void nativePostprocessingCountersDoNotCreateAnotherPhysicalLandmark() throws Exception {
        var before = TagParser.parseTag("{id:'minecraft:mscorridor',GD:2,hr:true,sc:true,hps:false,BB:[I;0,10,0,5,15,20]}");
        var after = before.copy(); after.putBoolean("hps",true); after.putInt("GD",7);
        assertEquals(PhysicalStructureIdentity.of(ID,List.of(before)),PhysicalStructureIdentity.of(ID,List.of(after)));
        after.putBoolean("hr",false);
        assertNotEquals(PhysicalStructureIdentity.of(ID,List.of(before)),PhysicalStructureIdentity.of(ID,List.of(after)));
        before.putString("id","example:custom");after=before.copy();after.putBoolean("hps",true);
        assertNotEquals(PhysicalStructureIdentity.of(ID,List.of(before)),PhysicalStructureIdentity.of(ID,List.of(after)));
        after=before.copy();after.putInt("GD",8);
        assertNotEquals(PhysicalStructureIdentity.of(ID,List.of(before)),PhysicalStructureIdentity.of(ID,List.of(after)));
    }
}
