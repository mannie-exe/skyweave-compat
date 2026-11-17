package design.inherent.skyweavecompat.encounters;

import net.minecraft.util.RandomSource;

public final class EncounterChance {
    /** Preserve the native random operation exactly at its standalone/default probability. */
    public static int patrol(RandomSource random,int bound,double percent){
        if(bound!=5)throw new IllegalStateException("Unexpected native patrol probability bound");
        if(percent==20)return random.nextInt(bound);
        return roll(random,percent)?0:1;
    }
    public static boolean roll(RandomSource random,double percent){
        if(!Double.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("Expected percentage 0..100");
        return percent==100 || percent>0 && random.nextDouble()<percent/100;
    }
    private EncounterChance(){}
}
