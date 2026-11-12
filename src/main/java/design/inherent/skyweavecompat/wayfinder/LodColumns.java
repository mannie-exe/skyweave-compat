package design.inherent.skyweavecompat.wayfinder;

import java.util.List;

/** Query-local observed columns; missing information is never interpreted as air. */
public interface LodColumns extends AutoCloseable {
    enum State { READY, PENDING, MISSING, INCOMPLETE, CLOSED, FAILED }
    record Band(int bottom, int top, boolean air) {}
    record Column(State state, List<Band> bands, int detail) {
        public Column(State state,List<Band> bands){this(state,bands,0);}
        public Column { bands = List.copyOf(bands);if(detail<0 || detail>8)throw new IllegalArgumentException("Invalid LOD cell detail"); }
        public static Column state(State state) { return new Column(state, List.of()); }
        /** Only observed intervals count; omitted depth and gaps remain unknown. */
        public boolean covers(double bottom, double top) {
            if(!Double.isFinite(bottom) || !Double.isFinite(top) || bottom>top)
                throw new IllegalArgumentException("Invalid observed interval");
            if(state!=State.READY)return false;
            double covered=bottom;
            for(var band:bands) {
                if(band.top()<=covered)continue;
                if(band.bottom()>covered)return false;
                if(band.top()>=top)return true;
                covered=band.top();
            }
            return false;
        }
    }
    Column poll(int blockX, int blockZ);
    default Column poll(int blockX,int blockZ,double bottom,double top) {
        var column=poll(blockX,blockZ);
        return column.state()==State.READY && !column.covers(bottom,top)
                ?Column.state(State.INCOMPLETE):column;
    }
    @Override void close();
}
