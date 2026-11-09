package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Set;

/** Disk metadata is only a conservative positive filter; native starts remain authoritative. */
final class SavedStructureStarts {
    static boolean possible(CompoundTag root, Set<String> targets) {
        if (root == null) return false;
        // Older formats must go through Minecraft's normal upgrade/read path, never a cached absence.
        if (!root.contains("DataVersion", Tag.TAG_ANY_NUMERIC) || root.getInt("DataVersion") < 2844) return true;
        var starts = root.getCompound("structures").getCompound("starts");
        for (var id : targets) {
            if (!starts.contains(id, Tag.TAG_COMPOUND)) continue;
            var start = starts.getCompound(id);
            if (!start.isEmpty() && !start.getString("id").equals("INVALID")) return true;
        }
        return false;
    }
    private SavedStructureStarts() {}
}
