package design.inherent.skyweavecompat.input;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local input origins only. Neither upstream key state nor original events are mutated. */
public final class TextInputEligibility {
    private final Set<Integer> suppressed = new HashSet<>();
    private boolean overflow;

    public void observe(Set<Integer> pressed, boolean textOwnsKeyboard, boolean windowActive) {
        if (!textOwnsKeyboard && windowActive) return;
        for (int key : pressed) if (key >= 0) {
            if (suppressed.size() == 512 && !suppressed.contains(key)) { overflow = true; return; }
            suppressed.add(key);
        }
    }

    public boolean allows(List<Integer> chord, boolean textOwnsKeyboard, boolean mouseOverSlot, boolean windowActive) {
        if (!windowActive || overflow) return false;
        boolean inventoryMouse = mouseOverSlot && chord.stream().anyMatch(key -> key < 0)
                && chord.stream().allMatch(key -> key < 0 || key >= 340 && key <= 347);
        if (inventoryMouse) return true;
        if (textOwnsKeyboard) return false;
        return chord.stream().noneMatch(suppressed::contains);
    }

    /** Run after all callbacks, so a release-triggered binding still sees its suppressed origin. */
    public void completed(Set<Integer> pressed) {
        suppressed.removeIf(key -> !pressed.contains(key));
        if (pressed.isEmpty()) overflow = false;
    }

    public void reset() { suppressed.clear(); overflow = false; }
}
