package design.inherent.skyweavecompat.wayfinder;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** One explicit sight query; each step consumes a bounded number of cells or LOD bands. */
public final class WayfinderTrace implements AutoCloseable {
    public enum Outcome { PENDING, HIT, MISS, MISSING, INCOMPLETE, CANCELLED, CONSTRUCT, FAILED }
    public record Result(Outcome outcome, SightRay.Point hit, WayfinderRegion region) {
        public Result(Outcome outcome,SightRay.Point hit){this(outcome,hit,null);}
        static Result of(Outcome outcome) { return new Result(outcome,null); }
    }
    private final SightRay ray;
    private final SightRay.Cursor cursor;
    private final LodColumns lod;
    private final List<String> blocked;
    private final WayfinderFrames frames;
    private LodColumns.Column column;
    private int nextBand;
    private double firstHit = Double.POSITIVE_INFINITY;
    private long workUnits;
    private Result result = Result.of(Outcome.PENDING);

    public WayfinderTrace(SightRay ray, SightRay.Interval domain, LodColumns lod, List<String> blocked, WayfinderFrames frames) {
        this.ray = ray; cursor = ray.cursor(domain); this.lod = lod; this.blocked = List.copyOf(blocked);
        this.frames = frames;
    }
    public double distance() { return cursor.distance(); }
    public long workUnits() { return workUnits; }
    public Result step(Level level, Entity player, int budget) {
        if (budget < 1 || budget > 2048) throw new IllegalArgumentException("Invalid trace slice");
        if (result.outcome() != Outcome.PENDING) return result;
        for (int work = 0; work < budget; work++) {
            if (cursor.complete()) return finish(Result.of(Outcome.MISS));
            if (frames != null && frames.storage(new net.minecraft.core.BlockPos(cursor.x(),cursor.y(),cursor.z())))
                return finish(Result.of(Outcome.CONSTRUCT));
            // ClientLevel.hasChunk() unconditionally returns true, even outside received terrain.
            if (level.getChunkSource().getChunk(cursor.x() >> 4,cursor.z() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false) != null) {
                workUnits++;
                column = null;
                var hit = WayfinderGeometry.hit(level,player,ray,cursor,blocked);
                if (hit != null) return finish(new Result(Outcome.HIT,new SightRay.Point(
                        hit.getLocation().x + ray.dx()*1e-6,hit.getLocation().y + ray.dy()*1e-6,hit.getLocation().z + ray.dz()*1e-6)));
                cursor.advance(); continue;
            }
            if (lod == null) return finish(Result.of(Outcome.MISSING));
            if (column == null) {
                // Do not demand unknown depth beyond a nearer observed hit, including vertical rays.
                double startY=ray.at(cursor.distance()).y(),endY=ray.at(cursor.cellExit()).y();
                column = lod.poll(cursor.x(),cursor.z(),Math.min(startY,endY),Math.max(startY,endY));
                if (column.state() == LodColumns.State.PENDING) { column = null; return result; }
                if (column.state() != LodColumns.State.READY) return finish(Result.of(switch (column.state()) {
                    case MISSING -> Outcome.MISSING;
                    case INCOMPLETE -> Outcome.INCOMPLETE;
                    case CLOSED -> Outcome.CANCELLED;
                    default -> Outcome.FAILED;
                }));
                nextBand = 0; firstHit = Double.POSITIVE_INFINITY;
            }
            if (nextBand < column.bands().size()) {
                workUnits++;
                var band = column.bands().get(nextBand++);
                if (!band.air()) {
                    int width=1<<column.detail(),x=Math.floorDiv(cursor.x(),width)*width,z=Math.floorDiv(cursor.z(),width)*width;
                    var intersect = ray.intersect(x,band.bottom(),z,(double)x+width,band.top(),(double)z+width);
                    if (intersect != null && intersect.exit() > cursor.distance()) {
                        double entry = Math.max(cursor.distance(),intersect.enter());
                        if (entry < cursor.cellExit()) firstHit = Math.min(firstHit,entry);
                    }
                }
            } else {
                workUnits++;
                if (Double.isFinite(firstHit)) return finish(new Result(Outcome.HIT,ray.at(firstHit + 1e-6),
                        column.detail()==0?null:WayfinderRegion.containing(cursor.x(),cursor.z(),column.detail())));
                cursor.advance(); column = null;
            }
        }
        return result;
    }
    private Result finish(Result result) { this.result = result; if (lod != null) lod.close(); return result; }
    @Override public void close() { finish(Result.of(Outcome.CANCELLED)); column = null; }
}
