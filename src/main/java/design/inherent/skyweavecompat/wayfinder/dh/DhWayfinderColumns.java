package design.inherent.skyweavecompat.wayfinder.dh;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import design.inherent.skyweavecompat.wayfinder.LodColumns;
import java.util.*;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import java.util.concurrent.CompletionException;

/** Exact DH 3.3.3 async read boundary; never generates native chunks or mutates terrain. */
public final class DhWayfinderColumns implements LodColumns {
    // Retired sessions cannot leave an unbounded tail of outstanding database reads.
    private static final BoundedReadExecutor READS = new BoundedReadExecutor();
    private final AbstractDhWorld world;
    private final IDhLevel level;
    private final ILevelWrapper wrapper;
    private final int minY, maxY;
    private final FullDataSourceProviderV2 provider;
    private final LinkedHashMap<Long,FullDataSourceV2> sources=new LinkedHashMap<>(4,.75f,true);
    private final LinkedHashMap<Long,Boolean> absent=new LinkedHashMap<>(128,.75f,true);
    private record CellKey(long section,int column) {}
    private final LinkedHashMap<CellKey,Column> columns=new LinkedHashMap<>(256,.75f,true);
    private boolean pending,closed;
    private State failure;
    private long reads;

    public DhWayfinderColumns(IDhApiLevelWrapper wrapper) {
        if (!(wrapper instanceof ILevelWrapper nativeWrapper)) throw new IllegalArgumentException("Unsupported DH wrapper");
        this.wrapper = nativeWrapper;
        world = SharedApi.getAbstractDhWorld();
        level = world == null ? null : world.getLevel(nativeWrapper);
        if (level == null) throw new IllegalStateException("DH level is unavailable");
        provider=level.getFullDataProvider();
        // DH 3.3.3 calls dimension height "maxHeight"; the native bounds are unambiguous.
        if (!(wrapper.getWrappedMcObject() instanceof net.minecraft.world.level.Level nativeLevel))
            throw new IllegalArgumentException("Expected a native DH level");
        minY = nativeLevel.getMinBuildHeight(); maxY = nativeLevel.getMaxBuildHeight();
    }

    @Override public synchronized Column poll(int x,int z) {
        return poll(x,z,minY,maxY);
    }
    @Override public synchronized Column poll(int x,int z,double bottom,double top) {
        if(!Double.isFinite(bottom) || !Double.isFinite(top) || bottom>top || bottom<minY || top>maxY)
            throw new IllegalArgumentException("Query outside native build bounds");
        if(closed)return Column.state(State.CLOSED);
        if(SharedApi.getAbstractDhWorld()!=world || world.getLevel(wrapper)!=level || level.getFullDataProvider()!=provider) {
            close();return Column.state(State.CLOSED);
        }
        if(failure!=null)return Column.state(failure);
        if(pending)return Column.state(State.PENDING);
        boolean incomplete=false;
        for(int detail=0;detail<=FullDataSourceProviderV2.ROOT_SECTION_DETAIL_LEVEL-FullDataSourceProviderV2.LEAF_SECTION_DETAIL_LEVEL;detail++) {
            int sectionWidth=64<<detail;
            long key=DhSectionPos.encode((byte)(6+detail),Math.floorDiv(x,sectionWidth),Math.floorDiv(z,sectionWidth));
            if(absent.get(key)!=null)continue;
            var cellKey=new CellKey(key,FullDataSourceV2.relativePosToIndex(
                    Math.floorMod(Math.floorDiv(x,1<<detail),64),Math.floorMod(Math.floorDiv(z,1<<detail),64)));
            var retained=columns.get(cellKey);
            if(retained!=null) {
                if(retained.state()==State.FAILED || retained.covers(bottom,top))return retained;
                incomplete|=retained.state()==State.INCOMPLETE || retained.state()==State.READY;continue;
            }
            var source=sources.get(key);
            if(source==null) {
                pending=true;
                try {
                    if(!READS.submit(() -> readStored(key),(received,error) -> {
                        synchronized(DhWayfinderColumns.this) {
                            try {
                                if(closed || error!=null || received==null || received.getPos()!=key) {
                                    if(received!=null)received.close();
                                    if(!closed)failure=State.FAILED;
                                } else if(received.isEmpty) {
                                    received.close();absent.put(key,true);
                                    while(absent.size()>128)absent.remove(absent.keySet().iterator().next());
                                } else {
                                    sources.put(key,received);
                                    while(sources.size()>2) {
                                        var iterator=sources.entrySet().iterator();var old=iterator.next();iterator.remove();old.getValue().close();
                                    }
                                }
                            } finally {pending=false;}
                        }
                    })) {pending=false;return Column.state(State.PENDING);}
                    reads++;
                } catch(RuntimeException error){pending=false;failure=State.FAILED;}
                return Column.state(failure==null?State.PENDING:failure);
            }
            var column=decode(source,x,z,detail);
            columns.put(cellKey,column);
            while(columns.size()>256)columns.remove(columns.keySet().iterator().next());
            if(column.state()==State.FAILED || column.covers(bottom,top))return column;
            incomplete|=column.state()==State.INCOMPLETE || column.state()==State.READY;
        }
        return Column.state(incomplete?State.INCOMPLETE:State.MISSING);
    }
    private FullDataSourceV2 readStored(long key) {
        // RemoteFullDataSourceProvider.get/getAsync can queue network synchronization;
        // the base getter also repairs/deletes rows. Preview owns neither operation.
        try(var dto=provider.repo.getByKey(key)) {
            return dto==null?FullDataSourceV2.createEmpty(key):dto.createDataSource(wrapper,null);
        } catch(Exception failure) {
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            throw new CompletionException(failure);
        }
    }
    private Column decode(FullDataSourceV2 source,int x,int z,int detail) {
        try {
            int cell=1<<detail;
            int relativeX=Math.floorMod(Math.floorDiv(x,cell),64),relativeZ=Math.floorMod(Math.floorDiv(z,cell),64);
            var data=source.getColumnAtRelPos(relativeX,relativeZ);
            if(data==null || data.isEmpty())return Column.state(State.MISSING);
            int index=FullDataSourceV2.relativePosToIndex(relativeX,relativeZ);
            // Parent presence alone says nothing about missing child contributions. DH propagates
            // the minimum generation step of all four children; incomplete parents cannot certify coverage.
            if(source.columnGenerationSteps.getByte(index)<EDhApiWorldGenerationStep.TERRAIN.value)
                return Column.state(State.INCOMPLETE);
            if(source.getDataDetailLevel()!=detail || data.size()>maxY-minY+1)return Column.state(State.FAILED);
            var bands=new ArrayList<Band>(data.size());
            for(int i=0;i<data.size();i++) {
                long value=data.getLong(i);
                int bottom=minY+FullDataPointUtil.getBottomY(value),top=bottom+FullDataPointUtil.getHeight(value);
                boolean air=source.mapping.getBlockStateWrapper(FullDataPointUtil.getId(value)).isAir();
                if(bottom<minY || top>maxY+(air?1:0) || bottom>=top)return Column.state(State.FAILED);
                if(bottom<maxY)bands.add(new Band(bottom,Math.min(top,maxY),air));
            }
            bands.sort(Comparator.comparingInt(Band::bottom));int covered=minY;
            for(var band:bands) {
                if(band.bottom()<covered)return Column.state(State.FAILED);
                covered=band.top();
            }
            // Native visual compression can omit lower depth. Certify only the requested
            // ray interval, never synthesize air beneath the retained observation.
            // Coarse material/height is a representative observation, not exact child collision.
            return new Column(State.READY,bands,detail);
        } catch(RuntimeException invalid){return Column.state(State.FAILED);}
    }
    public synchronized long readCount(){return reads;}
    @Override public synchronized void close() {
        closed=true;sources.values().forEach(FullDataSourceV2::close);sources.clear();absent.clear();columns.clear();
        // A running upstream read still owns its pooled result. Its callback disposes late data;
        // the shared gate remains held until that real work finishes, rather than cancelling its wrapper.
    }
}
