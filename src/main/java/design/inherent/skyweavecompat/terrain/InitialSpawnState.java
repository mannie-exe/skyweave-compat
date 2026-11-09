package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Records initial shared-spawn selection only; stores no player or team assignments. */
public final class InitialSpawnState extends SavedData {
    public static final String ID = "skyweave_compat_initial_spawn";
    public static final SavedData.Factory<InitialSpawnState> FACTORY = new SavedData.Factory<>(
            InitialSpawnState::new, (tag, registries) -> new InitialSpawnState(tag.getBoolean("selected")));
    private boolean selected;

    public InitialSpawnState() { this(false); }
    private InitialSpawnState(boolean selected) { this.selected = selected; }
    public boolean selected() { return selected; }
    public void markSelected() { selected = true; setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("selected", selected);
        return tag;
    }
}
