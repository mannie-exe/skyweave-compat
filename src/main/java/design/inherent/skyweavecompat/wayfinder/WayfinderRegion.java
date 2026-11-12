package design.inherent.skyweavecompat.wayfinder;

/** Horizontal extent of one observed coarse DH cell; native refinement uses the real full height. */
public record WayfinderRegion(int minX,int minZ,int detail) {
    public WayfinderRegion {
        if(detail<1 || detail>8)throw new IllegalArgumentException("Invalid coarse cell detail");
        int width=1<<detail;
        if(Math.floorMod(minX,width)!=0 || Math.floorMod(minZ,width)!=0
                || minX< -30000256 || minX>30000000 || minZ< -30000256 || minZ>30000000)
            throw new IllegalArgumentException("Invalid coarse cell bounds");
    }
    public int width(){return 1<<detail;}
    public static WayfinderRegion containing(int x,int z,int detail) {
        if(detail<1 || detail>8)throw new IllegalArgumentException("Invalid coarse cell detail");
        int width=1<<detail;return new WayfinderRegion(Math.floorDiv(x,width)*width,Math.floorDiv(z,width)*width,detail);
    }
    public SightRay.Interval interval(SightRay ray,SightRay.Interval domain,int minY,int maxY) {
        if(domain==null)return null;
        var bounds=ray.intersect(minX,minY,minZ,(double)minX+width(),maxY,(double)minZ+width());
        if(bounds==null)return null;
        double enter=Math.max(domain.enter(),bounds.enter()),exit=Math.min(domain.exit(),bounds.exit());
        return exit>enter?new SightRay.Interval(enter,exit):null;
    }
}
