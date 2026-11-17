package design.inherent.skyweavecompat.encounters;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncounterChanceTest {
    @Test void nativePatrolProbabilityPreservesEveryRandomResultAndFollowingState() {
        var nativeRandom=RandomSource.create(42);var adaptedRandom=RandomSource.create(42);
        for(int attempt=0;attempt<4096;attempt++) {
            assertEquals(nativeRandom.nextInt(5),EncounterChance.patrol(adaptedRandom,5,20));
            assertEquals(nativeRandom.nextLong(),adaptedRandom.nextLong());
        }
    }
    @Test void boundaryPercentagesDoNotConsumeRandomnessAndNonNativeUsesOneRoll() {
        var random=new CountingRandom();
        assertFalse(EncounterChance.roll(random,0));assertTrue(EncounterChance.roll(random,100));
        assertEquals(0,random.calls);
        EncounterChance.roll(random,5);assertEquals(1,random.calls);
        assertEquals(1,EncounterChance.patrol(random,5,0));
        assertEquals(0,EncounterChance.patrol(random,5,100));assertEquals(1,random.calls);
        for(double invalid:new double[]{-1,101,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->EncounterChance.roll(random,invalid));
        assertThrows(IllegalStateException.class,()->EncounterChance.patrol(random,4,15));
    }
    private static final class CountingRandom extends LegacyRandomSource {
        int calls;
        CountingRandom(){super(42);}
        @Override public double nextDouble(){calls++;return super.nextDouble();}
    }
}
