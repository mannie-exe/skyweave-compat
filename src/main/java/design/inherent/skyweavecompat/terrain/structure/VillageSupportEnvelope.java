package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.terrain.IslandContext;

/** Shared natural gap eligibility; CAP still requires the captured material-stage open-cap proof. */
public final class VillageSupportEnvelope {
    public enum Gap { OPEN, CAP, WATER, CAVITY, OCCUPIED, UNKNOWN }
    private VillageSupportEnvelope() {}

    public static Gap gap(IslandContext context,int sourceY,int footY) {
        long distance=(long)footY-sourceY;
        if(distance<1 || distance>9)throw new IllegalArgumentException("Support envelope exceeds shallow bounds");
        if(distance==1)return Gap.OPEN;
        var owner=context.solidOwner(sourceY);
        if(owner==null)return Gap.UNKNOWN;
        boolean cap=false;
        for(int y=sourceY+1;y<footY;y++) {
            for(var contribution:context.contributions(y)) {
                switch(contribution.occupancy()) {
                    case WATER -> {return Gap.WATER;}
                    case CAVITY -> {return Gap.CAVITY;}
                    case UNKNOWN -> {return Gap.UNKNOWN;}
                    case SOLID -> {
                        if(!contribution.body().equals(owner))return Gap.OCCUPIED;
                        cap=true;
                    }
                    case OUTSIDE -> {}
                }
            }
        }
        return cap?Gap.CAP:Gap.OPEN;
    }
}
