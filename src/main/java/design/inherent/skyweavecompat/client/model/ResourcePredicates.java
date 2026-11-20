package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.*;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Registry-dependent predicates bind once to the current synchronized registry, outside the frame loop. */
final class ResourcePredicates {
    private record Key(ResourceLocation type, JsonElement value) {}
    private final Map<Key, Bound> bindings = new HashMap<>();
    private final Supplier<HolderLookup.Provider> registries;

    ResourcePredicates(ResourceValueCache cache) {
        this(cache, () -> {
            var connection = Minecraft.getInstance().getConnection();
            return connection == null ? null : connection.registryAccess();
        });
    }
    ResourcePredicates(ResourceValueCache cache, Supplier<HolderLookup.Provider> registries) {
        this.registries = registries;
        cache.onClear(() -> bindings.values().forEach(Bound::clear));
    }
    Predicate<ResourceComponents.Input> compile(ResourceLocation id, JsonElement value) {
        validate(id, value);
        var type = BuiltInRegistries.ITEM_SUB_PREDICATE_TYPE.get(id);
        if (type == null) throw new JsonParseException("Unknown component predicate: " + id);
        var independent = type.codec().parse(JsonOps.INSTANCE, value);
        if (independent.result().isPresent()) {
            var predicate = independent.result().orElseThrow();
            return input -> predicate.matches(input.stack());
        }
        if (id.toString().equals("minecraft:writable_book_content"))
            throw new JsonParseException(independent.error().orElseThrow().message());
        var key = new Key(id, value.deepCopy());
        var bound = bindings.computeIfAbsent(key, ignored -> new Bound(key, type));
        var provider = registries.get();
        if (provider != null) bound.bind(provider);
        return input -> {
            var current = registries.get();
            if (current == null) return false;
            bound.bind(current);
            return bound.predicate != null && bound.predicate.matches(input.stack());
        };
    }
    private static void validate(ResourceLocation id, JsonElement value) {
        switch (id.toString()) {
            case "minecraft:enchantments", "minecraft:stored_enchantments" -> {
                if (!value.isJsonArray() || value.getAsJsonArray().size() > ResourceDefinitions.MAX_CASES)
                    throw new JsonParseException("Expected bounded enchantment predicates");
                for (var entry : value.getAsJsonArray()) {
                    var predicate = ResourceDefinitions.object(entry);
                    ResourceDefinitions.fields(predicate, Set.of("enchantments", "levels"), "enchantment predicate");
                    if (predicate.has("levels")) bounds(predicate.get("levels"));
                    if (predicate.has("enchantments")) {
                        var keys = predicate.get("enchantments");
                        if (keys.isJsonArray()) {
                            if (keys.getAsJsonArray().size() > ResourceDefinitions.MAX_CASES) throw new JsonParseException("Enchantment key list exceeds limit");
                            for (var key : keys.getAsJsonArray()) enchantmentKey(key, false);
                        } else enchantmentKey(keys, true);
                    }
                }
            }
            case "minecraft:writable_book_content" -> {
                var predicate = ResourceDefinitions.object(value);
                ResourceDefinitions.fields(predicate, Set.of("pages"), "writable book predicate");
                if (predicate.has("pages")) {
                    var pages = ResourceDefinitions.object(predicate.get("pages"));
                    ResourceDefinitions.fields(pages, Set.of("size"), "book page predicate");
                    if (pages.has("size")) bounds(pages.get("size"));
                }
            }
            default -> throw new JsonParseException("Unsupported component predicate: " + id);
        }
    }
    private static void enchantmentKey(JsonElement value, boolean allowTag) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected enchantment identifier");
        String key = value.getAsString();
        if (allowTag && key.startsWith("#")) key = key.substring(1);
        ResourceDefinitions.identifier(key);
    }
    private static void bounds(JsonElement value) {
        if (value.isJsonObject()) ResourceDefinitions.fields(value.getAsJsonObject(), Set.of("min", "max"), "integer bound");
        var parsed = MinMaxBounds.Ints.CODEC.parse(JsonOps.INSTANCE, value);
        if (parsed.result().isEmpty()) throw new JsonParseException(parsed.error().orElseThrow().message());
    }
    private static final class Bound {
        final Key key;
        final ItemSubPredicate.Type<?> type;
        HolderLookup.Provider registry;
        ItemSubPredicate predicate;
        Bound(Key key, ItemSubPredicate.Type<?> type) { this.key = key; this.type = type; }
        void bind(HolderLookup.Provider provider) {
            if (registry == provider) return;
            var result = type.codec().parse(provider.createSerializationContext(JsonOps.INSTANCE), key.value);
            // Availability belongs to the synchronized registry, not to the lifetime of a resource root.
            // A different server may supply this valid identifier without triggering a resource reload.
            registry = provider; predicate = result.result().orElse(null);
            if (predicate == null) LogUtils.getLogger().warn("Component predicate {} could not bind to synchronized registry: {}",
                    key.type, result.error().orElseThrow().message());
        }
        void clear() { registry = null; predicate = null; }
    }
}
