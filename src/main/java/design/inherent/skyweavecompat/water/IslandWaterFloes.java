package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import java.util.*;

/** Retain height from already cached native routes; never build a watershed or request chunks. */
public final class IslandWaterFloes {
    private static final int MAX_WORK=16384,MAX_RESULTS=2048;
    private IslandWaterFloes() {}
    public static List<double[]> nodesNear(double x,double z,double minimum,double maximum) {
        if(!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || Math.abs(x)>30000000 || Math.abs(z)>30000000 || minimum<0 || maximum<minimum || maximum>64)return List.of();
        var nodes=new ArrayList<double[]>();int work=0;
        int minX=Math.floorDiv((int)Math.floor(x-maximum),256),maxX=Math.floorDiv((int)Math.floor(x+maximum),256);
        int minZ=Math.floorDiv((int)Math.floor(z-maximum),256),maxZ=Math.floorDiv((int)Math.floor(z+maximum),256);
        for(int rx=minX;rx<=maxX;rx++)for(int rz=minZ;rz<=maxZ;rz++) {
            var paths=HydrologyManager.getMountainRegionPathsIfCached(rx,rz);
            for(var path:paths) {
                if(++work>MAX_WORK)return List.of();
                if(x<path.minX-maximum || x>path.maxX+maximum || z<path.minZ-maximum || z>path.maxZ+maximum)continue;
                int count=path.validNodeCount;
                if(count<0 || count>path.segmentsX.length || count>path.segmentsZ.length || count>path.segmentsY.length)return List.of();
                for(int i=0;i<count;i+=2) {
                    if(++work>MAX_WORK)return List.of();
                    double nx=path.segmentsX[i],nz=path.segmentsZ[i],distance=Math.hypot(nx-x,nz-z);
                    if(!Double.isFinite(distance))return List.of();
                    if(distance<minimum || distance>maximum)continue;
                    if(nodes.size()==MAX_RESULTS)return List.of();
                    nodes.add(new double[]{nx,nz,path.segmentsY[i]});
                }
            }
        }
        return List.copyOf(nodes);
    }
}
