package design.inherent.skyweavecompat.quests;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.api.ItemFilterAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import java.util.HashSet;
import java.util.Set;

/** Native FTB item matching for authored alternatives; no inventory, progress or packet owner. */
public final class QuestItemFilters implements ItemFilterAdapter {
    public static final String KEY = "skyweave_compat:quest_items";
    private static final int MAX_ITEMS = 64, MAX_ID_LENGTH = 256;

    public static void register() { FTBQuestsAPI.api().registerFilterAdapter(new QuestItemFilters()); }
    @Override public String getName() { return "Skyweave"; }

    @Override public boolean isFilterStack(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        // Recognize malformed owned payloads too, so native matching cannot fall
        // through and interpret a broken filter as an ordinary paper objective.
        return stack.is(Items.PAPER) && data != null && data.copyTag().contains(KEY);
    }

    @Override public boolean doesItemMatch(ItemStack filter, ItemStack candidate, HolderLookup.Provider registries) {
        return getMatcher(filter, registries).test(candidate);
    }

    @Override public Matcher getMatcher(ItemStack filter, HolderLookup.Provider registries) {
        if (!isFilterStack(filter)) return NO_MATCH;
        var data = filter.get(DataComponents.CUSTOM_DATA).copyTag();
        var selection = parse(data.get(KEY));
        if (selection == null) return NO_MATCH;
        return candidate -> !candidate.isEmpty() && (selection.tag() != null
                ? candidate.is(selection.tag())
                : candidate.getItemHolder().unwrapKey().map(key -> selection.items().contains(key.location())).orElse(false));
    }

    record Selection(Set<ResourceLocation> items, TagKey<Item> tag) {
        Selection { items = Set.copyOf(items); }
    }

    /** Bounded, versioned declarative selector; invalid or unknown fields match nothing. */
    static Selection parse(Tag raw) {
        if (!(raw instanceof CompoundTag value) || !value.contains("schema", Tag.TAG_INT) || value.getInt("schema") != 1)
            return null;
        if (value.getAllKeys().equals(Set.of("schema", "tag")) && value.contains("tag", Tag.TAG_STRING)) {
            var id = id(value.getString("tag"));
            return id == null ? null : new Selection(Set.of(), TagKey.create(Registries.ITEM, id));
        }
        if (!value.getAllKeys().equals(Set.of("schema", "items")) || !value.contains("items", Tag.TAG_LIST)) return null;
        var list = value.getList("items", Tag.TAG_STRING);
        if (list.isEmpty() || list.size() > MAX_ITEMS) return null;
        var items = new HashSet<ResourceLocation>();
        for (int index = 0; index < list.size(); index++) {
            var id = id(list.getString(index));
            if (id == null || !items.add(id)) return null;
        }
        return new Selection(items, null);
    }

    private static ResourceLocation id(String value) {
        return value.length() <= MAX_ID_LENGTH && value.indexOf(':') > 0 ? ResourceLocation.tryParse(value) : null;
    }

    @Override public boolean hasItemTagFilter() { return true; }
    @Override public ItemStack makeTagFilterStack(TagKey<Item> tag) {
        var selector = new CompoundTag(); selector.putInt("schema", 1); selector.putString("tag", tag.location().toString());
        var data = new CompoundTag(); data.put(KEY, selector);
        var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
}
