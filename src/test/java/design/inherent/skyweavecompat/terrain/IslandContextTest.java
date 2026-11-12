package design.inherent.skyweavecompat.terrain;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import static org.junit.jupiter.api.Assertions.*;

class IslandContextTest {
    private static IslandContext.Body body(long seed, int bottom, int top) {
        var island = new IslandDescriptor(IslandFamily.ANCHOR_PLATEAU, 0, top - 28, 0,
                200, 200, 200, 0, IslandShapeArchetype.CLASSIC, 28, top - bottom, 48,
                0, 0, 16, 16, .5, seed);
        return new IslandContext.Body(island, new TerrainColumn(bottom, top),
                new GeologicalFrame(bottom, top + 1), top, false);
    }

    @Test void overlappingExteriorOwnershipIsHeightAwareAndRetainsCavityOwners() {
        var low = body(1, 0, 160); var high = body(2, 110, 420);
        var context = new IslandContext(List.of(high, low));
        assertSame(low, context.owner(80, 0));
        assertSame(high, context.owner(350, 0));
        assertEquals(2, context.containing(130).size());
        assertSame(low, context.owner(130, 0));
        assertNull(context.owner(421, 0));
        assertSame(high, context.owner(421, 1));
        assertNull(context.owner(-20, 19));
        assertThrows(IllegalArgumentException.class, () -> context.owner(20, -1));
    }

    @Test void sourceOrderAndCallerMutationCannotChangeOwnership() {
        var a = body(1, 0, 200); var b = body(2, 0, 200);
        var mutable = new ArrayList<>(List.of(a, b));
        IslandContext.NaturalSampler solid=(body,y)->body.contains(y)?IslandContext.Occupancy.SOLID:IslandContext.Occupancy.OUTSIDE;
        var first = new IslandContext(mutable,solid); var second = new IslandContext(List.of(b, a),solid);
        mutable.clear();
        assertEquals(first.bodies(), second.bodies());
        assertSame(first.climate(), first.climate());
        for (int y = -10; y < 210; y++) assertEquals(
                first.climate().referenceY(y, design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT),
                second.climate().referenceY(y, design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT));
        for (int y = -10; y < 210; y++) assertEquals(first.owner(y, 10), second.owner(y, 10));
        assertThrows(UnsupportedOperationException.class, () -> first.bodies().clear());
    }

    @Test void capturedThermalWinnerAndPackedClientAgreeAtEveryHeight() {
        var low=body(1,0,200);var high=body(2,100,400);
        IslandContext.NaturalSampler sampler=(owner,y)->!owner.contains(y)?IslandContext.Occupancy.OUTSIDE:
                owner==low && y>=120 && y<=180?IslandContext.Occupancy.CAVITY:IslandContext.Occupancy.SOLID;
        var context=new IslandContext(List.of(high,low),sampler);
        var column=context.climate();
        var packet=new design.inherent.skyweavecompat.climate.ClimateChunk(0,0,java.util.Collections.nCopies(256,column));
        var packed=new design.inherent.skyweavecompat.climate.PackedClimateChunk(packet);
        var policy=design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT;
        for(int y=-1;y<=401;y++) {
            var owner=context.biomeOwner(y);
            if(owner!=null)assertEquals(64+y-owner.climateDatum(),column.referenceY(y,policy),"Thermal/material winner at "+y);
            assertEquals(column.referenceY(y,policy),packed.referenceY(0,y,0,policy));
        }
        var reverse=new IslandContext(List.of(low,high),sampler);
        assertEquals(column,reverse.climate());
    }

    @Test void unknownAndAlternatingWinnerExhaustionNeverPublishPartialClimate() {
        var low=body(1,0,200);var high=body(2,0,400);
        var unknown=new IslandContext(List.of(low));
        var missing=assertThrows(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.class,unknown::climate);
        assertEquals(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.Reason.UNKNOWN,missing.reason());
        assertSame(missing,assertThrows(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.class,unknown::climate));
        var alternating=new IslandContext(List.of(low,high),(owner,y)->!owner.contains(y)?IslandContext.Occupancy.OUTSIDE:
                owner==low && y%2==0?IslandContext.Occupancy.CAVITY:IslandContext.Occupancy.SOLID);
        var exhausted=assertThrows(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.class,alternating::climate);
        assertEquals(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.Reason.BUDGET,exhausted.reason());
    }

    @Test void provenSingleDomainFastPathIncludesWaterAndDoesNotSweepDensity() {
        var body=body(1,0,200);var samples=new java.util.concurrent.atomic.AtomicInteger();
        var context=new IslandContext(List.of(body),new IslandContext.NaturalSampler() {
            @Override public IslandContext.Occupancy at(IslandContext.Body owner,int y) {
                samples.incrementAndGet();return y>200?IslandContext.Occupancy.WATER:IslandContext.Occupancy.SOLID;
            }
            @Override public List<TerrainColumn> climateDomain(IslandContext.Body owner) {return List.of(new TerrainColumn(0,200),new TerrainColumn(201,240));}
        });
        assertEquals(List.of(new design.inherent.skyweavecompat.climate.ClimateColumn.Layer(0,240,200)),context.climate().layers());
        assertEquals(0,samples.get());
        var first=context.climate();java.util.stream.IntStream.range(0,100).parallel().forEach(i->assertSame(first,context.climate()));
    }

    @Test void unknownOverlapCannotBorrowAKnownContributorsThermalAuthority() {
        var low=body(1,100,200);var high=body(2,0,400);
        var context=new IslandContext(List.of(low,high),(owner,y)->!owner.contains(y)?IslandContext.Occupancy.OUTSIDE:
                owner==low?IslandContext.Occupancy.UNKNOWN:IslandContext.Occupancy.SOLID);
        var failure=assertThrows(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.class,context::climate);
        assertEquals(design.inherent.skyweavecompat.climate.ClimateColumn.Unavailable.Reason.UNKNOWN,failure.reason());
    }

    @Test void airFallbackRetainsCanonicalIdentityWhenHeightOrderDiffers() {
        var low=body(1,0,200);var template=body(2,300,500);var island=template.island();
        var shifted=new IslandDescriptor(island.family(),-1,island.centerY(),island.centerZ(),island.radiusX(),island.radiusZ(),
                island.maxRadius(),island.rotation(),island.archetype(),island.plateauHeight(),island.cliffDepth(),island.hangDepth(),
                island.hangOffsetX(),island.hangOffsetZ(),island.tailRadiusX(),island.tailRadiusZ(),island.erosionStrength(),island.seed());
        var high=new IslandContext.Body(shifted,template.exterior(),template.geology(),300,false);
        var context=new IslandContext(List.of(low,high),(owner,y)->owner.contains(y)?IslandContext.Occupancy.SOLID:IslandContext.Occupancy.OUTSIDE);
        assertSame(high,context.owner(250,256));
        assertEquals(14,context.climate().referenceY(250,design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT),
                "Equal-distance/equal-altitude air ties must retain canonical identity, not interval height order");
    }
    @Test void cavityOwnershipCannotStealAnotherContributorsSolidMaterial() {
        var low=body(1,0,200);var high=body(2,100,400);
        var context=new IslandContext(List.of(high,low),(owner,y) -> {
            if(!owner.contains(y))return IslandContext.Occupancy.OUTSIDE;
            return owner==low && y>=120 && y<=180 ? IslandContext.Occupancy.CAVITY:IslandContext.Occupancy.SOLID;
        });
        assertSame(low,context.owner(150,0));
        assertSame(high,context.solidOwner(150));
        assertSame(high,context.biomeOwner(150),"A nearer cavity datum must not select the biome inside another body's physical material");
        assertEquals(-186,context.climate().referenceY(150,design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT),
                "Captured thermal ownership must follow the upper physical material, not the lower cavity datum");
        assertEquals(2,context.contributions(150).size());
        assertSame(low,context.solidOwner(150,low.island(),145));
        assertSame(high,context.solidOwner(150,low.island(),151));
        assertNull(context.solidOwner(401));
        assertNull(new IslandContext(List.of(low)).solidOwner(150));
        assertNull(new IslandContext(List.of(low)).biomeOwner(150),"Unknown occupancy cannot certify underground ownership");
    }

    @Test void biomeOwnershipPreservesCavityWaterAndCanonicalTiesWithoutAirBorrowing() {
        var low=body(1,0,200);var high=body(2,100,400);
        var cavity=new IslandContext(List.of(low),(owner,y)->owner.contains(y)?IslandContext.Occupancy.CAVITY:IslandContext.Occupancy.OUTSIDE);
        assertSame(low,cavity.biomeOwner(150));assertNull(cavity.biomeOwner(201));
        IslandContext.NaturalSampler sample=(owner,y)->!owner.contains(y)?IslandContext.Occupancy.OUTSIDE:
                owner==low?IslandContext.Occupancy.CAVITY:IslandContext.Occupancy.WATER;
        assertSame(high,new IslandContext(List.of(low,high),sample).biomeOwner(150));
        assertSame(high,new IslandContext(List.of(high,low),sample).biomeOwner(150));
        var a=body(3,0,200);var b=body(4,0,200);
        sample=(owner,y)->owner.contains(y)?IslandContext.Occupancy.SOLID:IslandContext.Occupancy.OUTSIDE;
        assertEquals(new IslandContext(List.of(a,b),sample).biomeOwner(150),new IslandContext(List.of(b,a),sample).biomeOwner(150));
    }

}
