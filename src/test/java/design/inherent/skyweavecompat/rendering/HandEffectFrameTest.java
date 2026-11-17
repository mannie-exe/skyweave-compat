package design.inherent.skyweavecompat.rendering;

import design.inherent.skyweavecompat.client.HandEffectFrame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandEffectFrameTest {
    @Test void solidAndTranslucentShareOneInitializationAndOneComposite() {
        var frame = new HandEffectFrame(); var pipeline = new Object(); var level = new Object();
        frame.begin(pipeline, level);
        assertTrue(frame.enter(pipeline, level)); frame.leave(true);
        assertFalse(frame.enter(pipeline, level)); frame.leave(true);
        assertTrue(frame.finish(pipeline, level));
        assertFalse(frame.finish(pipeline, level));
        assertThrows(IllegalStateException.class, () -> frame.enter(pipeline, level));
    }

    @Test void skippedHandsDoNotCompositeAndExceptionsDiscardPartialMasks() {
        var frame = new HandEffectFrame(); var pipeline = new Object(); var level = new Object();
        frame.begin(pipeline, level); assertFalse(frame.finish(pipeline, level));
        frame.begin(pipeline, level); frame.enter(pipeline, level);
        assertFalse(frame.finish(pipeline, level)); // A buffer pass is still in progress.
        frame.leave(false); assertFalse(frame.finish(pipeline, level));
        assertThrows(IllegalStateException.class, () -> frame.enter(pipeline, level));
    }

    @Test void nestedPassesAndOwnershipChangesCannotCertifyOldWork() {
        var frame = new HandEffectFrame(); var pipeline = new Object(); var level = new Object();
        frame.begin(pipeline, level);
        assertTrue(frame.enter(pipeline, level)); assertFalse(frame.enter(pipeline, level));
        frame.leave(true); assertFalse(frame.finish(pipeline, level)); frame.leave(true);
        assertFalse(frame.finish(new Object(), level)); assertFalse(frame.finish(pipeline, new Object()));
        assertTrue(frame.finish(pipeline, level));
        frame.clear(); assertFalse(frame.finish(pipeline, level));
        assertThrows(IllegalStateException.class, () -> frame.leave(true));
        frame.begin(pipeline, level); assertTrue(frame.enter(pipeline, level)); frame.leave(true);
        assertTrue(frame.finish(pipeline, level));
    }
}
