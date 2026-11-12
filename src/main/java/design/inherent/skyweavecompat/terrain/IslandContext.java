package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.climate.ClimateColumn;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;

/** Natural contributor ownership, not a promise about a live block or completed hydrology. */
public final class IslandContext {
    public enum Occupancy { SOLID, CAVITY, WATER, OUTSIDE, UNKNOWN }
    @FunctionalInterface public interface NaturalSampler {
        Occupancy at(Body body, int y);
        /** Exact non-OUTSIDE/non-UNKNOWN domain, including natural water; null means unqualified. */
        default List<TerrainColumn> climateDomain(Body body) { return null; }
    }
    public record Contribution(Body body, Occupancy occupancy) {}
    private final List<Body> bodies;
    private final NaturalSampler natural;
    private volatile ClimateColumn climate;
    private volatile ClimateColumn.Unavailable climateFailure;
    public List<Body> bodies() { return bodies; }
    public ClimateColumn climate() {
        var captured=climate;if(captured!=null)return captured;
        synchronized(this) {
            if(climateFailure!=null)throw climateFailure;
            if(climate==null)try {climate=captureClimate();}
            catch(ClimateColumn.Unavailable unavailable) {
                climateFailure=unavailable;throw unavailable;
            }
            return climate;
        }
    }
    public record Key(int x, int z, org.sathrek.sky_archipelago.config.SkyIslandSettings settings) {
        public Key { Objects.requireNonNull(settings); }
    }
    public record Body(IslandDescriptor island, TerrainColumn exterior, GeologicalFrame geology,
            int climateDatum, boolean fringe) {
        public Body {
            Objects.requireNonNull(island); Objects.requireNonNull(exterior); Objects.requireNonNull(geology);
            if (!exterior.exists()) throw new IllegalArgumentException("Empty contributor exterior");
        }
        public boolean contains(int y) { return exterior.contains(y); }
        public long distance(int y) {
            return y < exterior.bottomY() ? (long)exterior.bottomY() - y
                    : y > exterior.topY() ? (long)y - exterior.topY() : 0;
        }
    }

    private static final Comparator<Body> IDENTITY = Comparator
            .comparingInt((Body body) -> body.island().centerX())
            .thenComparingInt(body -> body.island().centerZ())
            .thenComparingInt(body -> body.island().centerY())
            .thenComparingInt(body -> body.island().family().ordinal())
            .thenComparingLong(body -> body.island().seed());

    public static final IslandContext EMPTY = new IslandContext(List.of());

    public IslandContext(List<Body> bodies) {
        this(bodies, (body,y) -> body.contains(y) ? Occupancy.UNKNOWN : Occupancy.OUTSIDE);
    }
    public IslandContext(List<Body> bodies, NaturalSampler natural) {
        this.natural = Objects.requireNonNull(natural);
        this.bodies = bodies.stream().sorted(IDENTITY).toList();
        if(this.bodies.isEmpty())climate=ClimateColumn.EMPTY;
    }

    private record Domain(Body body,List<TerrainColumn> ranges,boolean qualified) {}
    private record ThermalSpan(Body owner,int bottom,int top) {}
    private ClimateColumn captureClimate() {
        var domains=new java.util.ArrayList<Domain>();var boundaries=new java.util.TreeSet<Integer>();
        int rangeCount=0;
        for(var body:bodies) {
            var known=natural.climateDomain(body);boolean qualified=known!=null;
            var ranges=qualified?List.copyOf(known):List.of(body.exterior());
            if(ranges.size()>4096-rangeCount)throw climateUnavailable(false);rangeCount+=ranges.size();
            for(var range:ranges) {
                if(!range.exists() || range.bottomY() < -2048 || range.topY() >= 2048)throw climateUnavailable(true);
                boundaries.add(range.bottomY());boundaries.add(range.topY()+1);
            }
            domains.add(new Domain(body,ranges,qualified));
        }
        var layers=new java.util.ArrayList<ThermalSpan>();
        var edges=List.copyOf(boundaries);
        for(int i=0;i+1<edges.size();i++) {
            int bottom=edges.get(i),top=edges.get(i+1)-1;
            var active=domains.stream().filter(domain->domain.ranges().stream().anyMatch(range->range.contains(bottom))).toList();
            if(active.isEmpty())continue;
            // Only a proven single owner may bypass occupancy sampling. Even equal-datum
            // overlaps retain owner identity for canonical air-fallback ties against other bodies.
            if(active.size()==1 && active.getFirst().qualified()) {
                climateLayer(layers,bottom,top,active.getFirst().body());continue;
            }
            for(int y=bottom;y<=top;y++) {
                var owner=biomeOwner(y,true);
                if(owner==null) {
                    if(active.stream().anyMatch(Domain::qualified))throw climateUnavailable(true);
                } else climateLayer(layers,y,y,owner);
            }
        }
        return layers.isEmpty()?ClimateColumn.EMPTY
                :new ClimateColumn(layers.stream().sorted(java.util.Comparator
                        .comparingInt((ThermalSpan layer)->bodies.indexOf(layer.owner())).thenComparingInt(ThermalSpan::bottom))
                        .map(layer->new ClimateColumn.Layer(layer.bottom(),layer.top(),layer.owner().climateDatum())).toList());
    }
    private static ClimateColumn.Unavailable climateUnavailable(boolean unknown) {
        return new ClimateColumn.Unavailable(unknown
                ?ClimateColumn.Unavailable.Reason.UNKNOWN
                :ClimateColumn.Unavailable.Reason.BUDGET);
    }
    private static void climateLayer(java.util.ArrayList<ThermalSpan> layers,
            int bottom,int top,Body owner) {
        if(!layers.isEmpty()) {
            var last=layers.getLast();
            if(last.top()+1==bottom && last.owner()==owner) {
                layers.set(layers.size()-1,new ThermalSpan(owner,last.bottom(),top));return;
            }
        }
        if(layers.size()>=ClimateColumn.MAX_LAYERS)throw climateUnavailable(false);
        layers.add(new ThermalSpan(owner,bottom,top));
    }

    /** Intrinsic pre-watershed state; this is not a query of live or fully decorated blocks. */
    public Occupancy naturalAt(Body body, int y) { return natural.at(body,y); }
    public List<Contribution> contributions(int y) {
        return bodies.stream().map(body -> new Contribution(body,naturalAt(body,y)))
                .filter(value -> value.occupancy()!=Occupancy.OUTSIDE).toList();
    }
    public Body solidOwner(int y) { return solidOwner(y,null,Integer.MAX_VALUE); }
    /** Native biome depth follows intrinsic material first, then water or owned cavity air. */
    public Body biomeOwner(int y) { return biomeOwner(y,false); }
    private Body biomeOwner(int y,boolean requireKnown) {
        Body selected=null;int priority=Integer.MAX_VALUE;long altitude=Long.MAX_VALUE;
        for(var body:bodies) {
            int rank=switch(naturalAt(body,y)) {
                case SOLID -> 0;
                case WATER -> 1;
                case CAVITY -> 2;
                case OUTSIDE -> Integer.MAX_VALUE;
                case UNKNOWN -> {
                    if(requireKnown)throw climateUnavailable(true);
                    yield Integer.MAX_VALUE;
                }
            };
            if(rank==Integer.MAX_VALUE)continue;
            long datum=Math.abs((long)y-body.climateDatum());
            if(rank<priority || rank==priority && datum<altitude) {
                selected=body;priority=rank;altitude=datum;
            }
        }
        return selected;
    }
    /** Completed generation may supply its already-resolved river-bed protection, never a planner callback. */
    public Body solidOwner(int y, org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor protectedHost, int protectedFloor) {
        Body selected=null;long altitude=Long.MAX_VALUE;
        for(var body:bodies) {
            boolean protectedBed=body.island().equals(protectedHost) && y>=protectedFloor && body.contains(y);
            if(!protectedBed && naturalAt(body,y)!=Occupancy.SOLID)continue;
            long datum=Math.abs((long)y-body.climateDatum());
            if(datum<altitude) { selected=body;altitude=datum; }
        }
        return selected;
    }

    /** All cavity/exterior owners remain available even when another contributor is solid. */
    public List<Body> containing(int y) { return bodies.stream().filter(body -> body.contains(y)).toList(); }

    /** Stable height-qualified ownership; explicitly bounded air reach is for surface consumers. */
    public Body owner(int y, int airReach) {
        if (airReach < 0) throw new IllegalArgumentException("Negative air reach");
        Body selected = null;
        long nearest = Long.MAX_VALUE, altitude = Long.MAX_VALUE;
        for (var body : bodies) {
            long distance = body.distance(y), datum = Math.abs((long)y - body.climateDatum());
            if (distance <= airReach && (distance < nearest || distance == nearest && datum < altitude)) {
                selected = body; nearest = distance; altitude = datum;
            }
        }
        // Equal scores retain the canonical identity order established once at construction.
        return selected;
    }
}
