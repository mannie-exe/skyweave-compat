package design.inherent.skyweavecompat.terrain;

public interface GeologicalMaterial {
    void skyweave$setVeins(IslandVeins veins);
    void skyweave$setContext(IslandContext context, IslandStyles styles);
    void skyweave$protectBed(org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor owner, int floor);
}
