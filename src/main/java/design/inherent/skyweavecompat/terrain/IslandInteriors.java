package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ResolvedContributor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import java.util.*;

/** Subtract each contributor's interior before union, within the admitted exterior intervals. */
public final class IslandInteriors {
    @FunctionalInterface
    public interface BedProtection {
        /** Lowest retained bed cell, or MIN_VALUE when this contributor has no accepted water cut. */
        int floor(IslandDescriptor host, int x, int z, TerrainColumn exterior);
    }
    public enum Kind { BASELINE, NOISE, HOLLOW }
    private final InteriorProfile profile;
    private final IslandStyles styles;
    private final NormalNoise passageA, passageB, chamber;

    public IslandInteriors(RandomState random, InteriorProfile profile) {
        this(random, profile, null);
    }
    public IslandInteriors(RandomState random, InteriorProfile profile, IslandStyles styles) {
        this.profile = profile;
        this.styles = styles;
        // Opaque legacy random salt: changing it changes terrain for the same seed.
        var factory = random.getOrCreateRandomFactory(ResourceLocation.parse("create_sky_compat:interiors"));
        passageA = NormalNoise.create(factory.fromHashOf("passage_a"), new NormalNoise.NoiseParameters(-3, 1.0, 0.5));
        passageB = NormalNoise.create(factory.fromHashOf("passage_b"), new NormalNoise.NoiseParameters(-3, 1.0, 0.5));
        chamber = NormalNoise.create(factory.fromHashOf("chamber"), new NormalNoise.NoiseParameters(-3, 1.0, 0.5));
    }

    public static Kind kind(IslandDescriptor island, InteriorProfile profile) {
        if (island.cliffDepth() < profile.shellThickness() * 4) return Kind.BASELINE;
        double selection = unit(island.seed() ^ 0x60bb123f7a84a1d5L);
        return selection < profile.hollowChance() ? Kind.HOLLOW
                : selection < profile.noiseChance() + profile.hollowChance() ? Kind.NOISE : Kind.BASELINE;
    }

    /** Planned body interior; fractured HOLLOW pieces retain the parent's shell rather than a new cell lining. */
    public static Kind kind(IslandDescriptor island, InteriorProfile profile, IslandStyles.Kind style) {
        return style==IslandStyles.Kind.DEEP_DARK?Kind.HOLLOW:kind(island,profile);
    }

    public ColumnData carve(int x, int z, SkyIslandSettings settings, ColumnData exterior,
            List<ResolvedContributor> contributors) {
        return carve(x, z, settings, exterior, contributors, null);
    }

    public ColumnData carve(int x, int z, SkyIslandSettings settings, ColumnData exterior,
            List<ResolvedContributor> contributors, BedProtection protection) {
        if (exterior.segments().isEmpty()) return exterior;
        var allowed = new BitSet(GenerationProfile.MAX_Y - GenerationProfile.MIN_Y);
        for (var segment : exterior.segments()) allowed.set(segment.bottomY() - GenerationProfile.MIN_Y,
                segment.topY() + 1 - GenerationProfile.MIN_Y);
        var retained = new BitSet(allowed.length());
        boolean hasInterior = false;
        for (var contributor : contributors) {
            var slice = slice(x, z, contributor, protection);
            if (slice == null) continue;
            hasInterior |= slice.kind != Kind.BASELINE;
            if (slice.kind == Kind.BASELINE) {
                var solid = new BitSet(allowed.length());
                solid.set(slice.bottom - GenerationProfile.MIN_Y, slice.top + 1 - GenerationProfile.MIN_Y);
                solid.and(allowed); retained.or(solid);
                continue;
            }
            for (int y = slice.bottom; y <= slice.top; y++) {
                int index = y - GenerationProfile.MIN_Y;
                if (allowed.get(index) && slice.retains(y)) retained.set(index);
            }
        }
        // Preserve native river/water/overlay metadata; a cavity is not another island.
        var result = new ColumnData(intervals(retained), exterior.profile(), exterior.dominantDescriptor(),
                exterior.waterArchetype(), exterior.river(), exterior.overlayExposedSegments());
        ((LayeredColumn)(Object)result).skyweave$exterior(exterior.segments(),
                hasInterior || contributors.stream().anyMatch(contributor -> kind(contributor.descriptor(), profile,
                        styles==null?IslandStyles.Kind.NATIVE:styles.style(contributor.descriptor()).kind()) != Kind.BASELINE));
        return result;
    }

    /** Immutable one-contributor cavity decision, before union removes its identity. */
    public final class Slice {
        private final IslandDescriptor island;
        private final int x, z, bottom, top, protectedFloor, skylightFloor;
        private final Kind kind;
        private final ParentLandBody.Frame frame;
        private final boolean openings;
        private final org.sathrek.sky_archipelago.worldgen.generator.field.WaterArchetypeColumnProfile water;
        private Slice(IslandDescriptor island, int x, int z, int bottom, int top, Kind kind,
                int protectedFloor, int skylightFloor, boolean openings,
                org.sathrek.sky_archipelago.worldgen.generator.field.WaterArchetypeColumnProfile water,
                ParentLandBody.Frame frame) {
            this.island=island;this.x=x;this.z=z;this.bottom=bottom;this.top=top;this.kind=kind;
            this.protectedFloor=protectedFloor;this.skylightFloor=skylightFloor;this.water=water;
            this.openings=openings;
            this.frame=frame;
        }
        public boolean retains(int y) {
            return y>=bottom && y<=top && (kind==Kind.BASELINE
                    || protectedFloor!=Integer.MIN_VALUE && y>=protectedFloor
                    || water.containsProtectedSealAt(y)
                    || y<skylightFloor && !removed(island,x,frame==null?y:frame.parentY(y),z,
                            frame==null?bottom:frame.parent().bottomY(),frame==null?top:frame.parent().topY(),
                            kind==Kind.HOLLOW,openings));
        }
        public boolean waterAt(int y) { return water.containsWaterAt(y); }
        public TerrainColumn waterDomain() {
            return water.water()?new TerrainColumn(water.solidSurfaceY()+1,water.fluidSurfaceY()):TerrainColumn.EMPTY;
        }
        public boolean protectedSealAt(int y) { return water.containsProtectedSealAt(y); }
    }

    /** A null protection reads only intrinsic shape; it never initiates watershed planning. */
    public Slice slice(int x, int z, ResolvedContributor contributor, BedProtection protection) {
        var descriptor=contributor.descriptor();
        var style=styles==null?null:styles.style(descriptor);
        var kind=kind(descriptor,profile,style==null?IslandStyles.Kind.NATIVE:style.kind());
        var water=contributor.preparedDensity().waterArchetype();
        var body=water.exists()?new TerrainColumn(water.underbellyBottomY(),water.solidSurfaceY())
                :contributor.preparedDensity().landBody();
        if(!body.exists()) return null;
        int bottom=Math.max(GenerationProfile.MIN_Y,Math.max(contributor.minY(),body.bottomY()));
        int top=Math.min(GenerationProfile.MAX_Y-1,Math.min(contributor.maxY(),body.topY()));
        if(top<bottom) return null;
        boolean special = false;
        ParentLandBody.Frame frame = null;
        if(styles!=null) {
            var horizontal=contributor.horizontal();
            special = style.kind()==IslandStyles.Kind.DEEP_DARK;
            boolean fringe=((FringeColumn)(Object)horizontal).skyweave$fragment()!=null;
            if(fringe)kind=Kind.BASELINE;
            if(!fringe && style.kind()!=IslandStyles.Kind.NATIVE) {
                if(kind==Kind.HOLLOW) {
                    frame=((ParentLandBody)(Object)contributor.preparedDensity()).skyweave$parentLandBody();
                    if(frame==null)throw new IllegalStateException("Fractured HOLLOW contributor lost its parent body");
                } else {
                    var remnant=FragmentedExterior.sample(descriptor.seed(),horizontal.localX(),horizontal.localZ(),styles.profile().fractures());
                    if(top-bottom+1<profile.shellThickness()*4 || remnant.conservativeClearance()<profile.shellThickness())kind=Kind.BASELINE;
                }
            }
        }
        boolean openings = special || unit(descriptor.seed() ^ 0x329df348abc51d27L) < profile.openingChance();
        if(kind==Kind.BASELINE) return new Slice(descriptor,x,z,bottom,top,kind,Integer.MIN_VALUE,Integer.MAX_VALUE,openings,water,null);
        int floor=protection==null?Integer.MIN_VALUE:protection.floor(descriptor,x,z,new TerrainColumn(bottom,top));
        int skylight=skylightFloor(descriptor,x,z,frame==null?bottom:frame.parent().bottomY(),
                frame==null?top:frame.parent().topY(),kind,openings);
        if(frame!=null && skylight!=Integer.MAX_VALUE)skylight=frame.physicalY(skylight);
        return new Slice(descriptor,x,z,bottom,top,kind,floor,skylight,openings,water,frame);
    }

    private static List<TerrainColumn> intervals(BitSet bits) {
        var segments = new ArrayList<TerrainColumn>();
        for (int top = bits.length() - 1; top >= 0;) {
            int bottom = bits.previousClearBit(top) + 1;
            segments.add(new TerrainColumn(bottom + GenerationProfile.MIN_Y, top + GenerationProfile.MIN_Y));
            top = bits.previousSetBit(bottom - 1);
        }
        return List.copyOf(segments);
    }

    private boolean removed(IslandDescriptor island, int x, int y, int z, int bottom, int top, boolean hollow, boolean openings) {
        double dx = x - island.centerX(), dz = z - island.centerZ();
        double middle = island.centerY() - island.cliffDepth() * 0.35;
        if (openings && sideEntrance(island, dx, y - middle, dz)) return true;
        return cavity(island, x, y, z, bottom, top, hollow);
    }

    private int skylightFloor(IslandDescriptor island, int x, int z, int bottom, int top, Kind kind, boolean openings) {
        if (!profile.skylights().enabled() || kind != Kind.HOLLOW && !profile.skylights().noiseInteriors()
                || !openings
                || !SkylightShape.contains(island.seed(), x - island.centerX(), z - island.centerZ(), profile.skylights()))
            return Integer.MAX_VALUE;
        double middle = island.centerY() - island.cliffDepth() * 0.35;
        double height = island.cliffDepth() * profile.hollowHeight();
        // Three local witnesses bound the work and preserve pillars without drilling to the floor.
        for (double fraction : new double[]{0.65, 0.5, 0.35}) {
            int y = (int)Math.floor(middle + height * fraction);
            if (!cavity(island, x, y, z, bottom, top, kind == Kind.HOLLOW)) continue;
            boolean clear = true;
            if (kind == Kind.HOLLOW) for (int check = y; check <= Math.min(top, (int)Math.ceil(middle + height * 1.1)); check++) {
                if (pillar(island, x - island.centerX(), check, z - island.centerZ())) { clear = false; break; }
            }
            if (clear) return y;
        }
        return Integer.MAX_VALUE;
    }

    private boolean cavity(IslandDescriptor island, int x, int y, int z, int bottom, int top, boolean hollow) {
        double dx = x - island.centerX(), dz = z - island.centerZ();
        double middle = island.centerY() - island.cliffDepth() * 0.35;
        double h = island.cliffDepth() * profile.hollowHeight();
        if (y - bottom < profile.shellThickness() || top - y < profile.shellThickness()) return false;
        // Keep ragged outer shoulders solid, except for the explicitly selected entrance.
        double radial = dx * dx / (island.radiusX() * (double)island.radiusX())
                + dz * dz / (island.radiusZ() * (double)island.radiusZ());
        if (radial > 0.9 * 0.9) return false;
        double nx = x * 8 / profile.noiseHorizontalScale(), ny = y * 8 / profile.noiseVerticalScale();
        double nz = z * 8 / profile.noiseHorizontalScale();
        if (hollow) {
            double dy = (y - middle) / h;
            double shape = radial / (profile.hollowRadius() * profile.hollowRadius()) + dy * dy;
            if (shape >= 1 + chamber.getValue(nx * 0.6, ny * 0.6, nz * 0.6) * 0.18) return false;
            return !pillar(island, dx, y, dz);
        }
        return (Math.abs(passageA.getValue(nx, ny, nz)) < profile.passageWidth()
                && Math.abs(passageB.getValue(nx, ny, nz)) < profile.passageWidth())
                || chamber.getValue(nx * 0.6, ny * 0.6, nz * 0.6) > profile.chamberThreshold();
    }

    /** Three retained columns join the roof and floor; skylights must not sever them. */
    private static boolean pillar(IslandDescriptor island, double dx, int y, double dz) {
        for (int i = 0; i < 3; i++) {
            double angle = island.rotation() + i * Math.PI * 2 / 3;
            double distance = island.maxRadius() * (0.14 + unit(island.seed() + i * 437L) * 0.13);
            double px = Math.cos(angle) * distance + Math.sin(y * 0.035 + i) * 3;
            double pz = Math.sin(angle) * distance + Math.cos(y * 0.028 + i) * 3;
            double radius = 7 + unit(island.seed() ^ (i + 992L)) * 7;
            if ((dx - px) * (dx - px) + (dz - pz) * (dz - pz) < radius * radius) return true;
        }
        return false;
    }

    private static boolean sideEntrance(IslandDescriptor island, double dx, double dy, double dz) {
        double c = Math.cos(island.rotation()), s = Math.sin(island.rotation());
        double along = dx * c + dz * s, across = dz * c - dx * s;
        double rise = Math.max(0, along) * 0.16;
        if (along > 0 && Math.abs(across) < 8 && Math.abs(dy - rise) < 8) return true;
        return false;
    }

    private static double unit(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }
}
