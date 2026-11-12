package design.inherent.skyweavecompat.climate;

/** Compact immutable client metadata; its lifetime belongs to the native loaded chunk. */
public final class PackedClimateChunk {
    private final int x,z;
    private final int[] offsets=new int[257];
    private final long[] layers;
    public PackedClimateChunk(ClimateChunk chunk) {
        x=chunk.x();z=chunk.z();int count=0;
        for(int i=0;i<256;i++) { offsets[i]=count;count+=chunk.columns().get(i).layers().size(); }
        offsets[256]=count;layers=new long[count];int at=0;
        for(var column:chunk.columns())for(var layer:column.layers())
            layers[at++]=(long)(layer.bottom()+2048) | (long)(layer.top()+2048)<<12 | (long)(layer.datum()+2048)<<24;
    }
    public int layerCount() { return layers.length; }
    public Integer referenceY(int blockX,int y,int blockZ,ClimateProfile policy) {
        if(Math.floorDiv(blockX,16)!=x || Math.floorDiv(blockZ,16)!=z)throw new IllegalArgumentException("Climate query outside chunk");
        int index=(blockX&15)*16+(blockZ&15),selected=0;boolean found=false;
        long nearest=Long.MAX_VALUE,altitude=Long.MAX_VALUE;
        for(int i=offsets[index];i<offsets[index+1];i++) {
            long packed=layers[i];int bottom=(int)(packed&4095)-2048,top=(int)(packed>>12&4095)-2048,datum=(int)(packed>>24&4095)-2048;
            long distance=y<bottom?(long)bottom-y:y>top?(long)y-top:0,delta=Math.abs((long)y-datum);
            if(distance<=policy.airReach() && (distance<nearest || distance==nearest && delta<altitude)) {
                selected=datum;found=true;nearest=distance;altitude=delta;
            }
        }
        return found?ClimateColumn.referenceY(y,selected,policy):null;
    }
}
