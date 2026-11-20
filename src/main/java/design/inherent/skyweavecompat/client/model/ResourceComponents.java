package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GrassColor;
import static design.inherent.skyweavecompat.client.model.ResourceValueCache.Kind.*;

/** Explicit projections between modern resource values and native 1.21.1 components. */
final class ResourceComponents {
    record Input(ItemStack stack, LivingEntity entity, ItemDisplayContext display) {}
    private record Enchantments(Map<ResourceLocation, Integer> levels) {
        Enchantments { levels = Map.copyOf(levels); }
    }
    private record Effect(ResourceLocation id, int duration) {}
    private record Effects(List<Effect> effects) {
        Effects { effects = List.copyOf(effects); }
    }
    private record PotionColor(Optional<Integer> custom, OptionalInt effects) {}
    private final ResourceValueCache cache;

    ResourceComponents(ResourceValueCache cache) { this.cache = cache; }

    ResourceDefinitions.Selector<Input> selector(String component) {
        return switch (component) {
            case "minecraft:stored_enchantments", "minecraft:enchantments" -> selector(ResourceComponents::enchantments,
                    input -> cache.project(ENCHANTMENTS, input.stack.get(component.equals("minecraft:stored_enchantments")
                            ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS), ResourceComponents::enchantments));
            case "minecraft:suspicious_stew_effects" -> selector(ResourceComponents::effects,
                    input -> cache.project(STEW, input.stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS), ResourceComponents::effects));
            case "minecraft:custom_name" -> selector(value -> {
                var decoded = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value);
                return decoded.result().orElseThrow(() -> new JsonParseException(decoded.error().orElseThrow().message()));
            }, input -> Objects.requireNonNullElse(input.stack.get(DataComponents.CUSTOM_NAME), ResourceValueCache.ABSENT));
            case "minecraft:painting/variant" -> selector(ResourceComponents::key, input -> {
                Object value = cache.project(PAINTING, input.stack.get(DataComponents.ENTITY_DATA), data -> variant((CustomData) data));
                if (!(value instanceof ResourceLocation id)) return ResourceValueCache.ABSENT;
                var level = input.entity == null ? Minecraft.getInstance().level : input.entity.level();
                return level != null && level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).containsKey(id)
                        ? id : ResourceValueCache.ABSENT;
            });
            case "minecraft:axolotl/variant" -> selector(ResourceComponents::key,
                    input -> cache.project(AXOLOTL, input.stack.get(DataComponents.BUCKET_ENTITY_DATA), data -> {
                        var tag = ((CustomData) data).getUnsafe();
                        if (!tag.contains("Variant", 3)) return ResourceValueCache.ABSENT;
                        int variant = tag.getInt("Variant");
                        for (var candidate : Axolotl.Variant.values()) if (candidate.getId() == variant)
                            return ResourceLocation.withDefaultNamespace(candidate.getSerializedName());
                        return ResourceValueCache.ABSENT;
                    }));
            default -> throw new JsonParseException("Unsupported component projection: " + component);
        };
    }

    int constant(JsonElement value) { return color(value) | 0xff000000; }

    java.util.function.ToIntFunction<Input> tint(String type, JsonObject definition) {
        return switch (type) {
            case "minecraft:constant" -> {
                ResourceDefinitions.fields(definition, Set.of("type", "value"), "constant tint");
                int color = constant(ResourceDefinitions.required(definition, "value")); yield input -> color;
            }
            case "minecraft:grass" -> {
                ResourceDefinitions.fields(definition, Set.of("type", "temperature", "downfall"), "grass tint");
                float temperature = ResourceDefinitions.floating(definition, "temperature", Double.NaN);
                float downfall = ResourceDefinitions.floating(definition, "downfall", Double.NaN);
                if (temperature < 0 || temperature > 1 || downfall < 0 || downfall > 1)
                    throw new JsonParseException("Grass tint temperature/downfall must be in [0,1]");
                yield input -> GrassColor.get(temperature, downfall) | 0xff000000;
            }
            case "minecraft:potion" -> {
                ResourceDefinitions.fields(definition, Set.of("type", "default"), "potion tint");
                int fallback = constant(ResourceDefinitions.required(definition, "default"));
                yield input -> {
                    Object value = cache.project(POTION, input.stack.get(DataComponents.POTION_CONTENTS), component -> {
                        var contents = (PotionContents) component;
                        return new PotionColor(contents.customColor(), PotionContents.getColorOptional(contents.getAllEffects()));
                    });
                    if (!(value instanceof PotionColor colors)) return fallback;
                    return colors.custom.orElseGet(() -> colors.effects.orElse(fallback)) | 0xff000000;
                };
            }
            default -> throw new JsonParseException("Unsupported resource tint: " + type);
        };
    }

    private static ResourceDefinitions.Selector<Input> selector(Function<JsonElement, Object> decode, Function<Input, Object> current) {
        return new ResourceDefinitions.Selector<>() {
            public Object decode(JsonElement value) { return decode.apply(value); }
            public Object current(Input context) { return current.apply(context); }
        };
    }

    private static Object enchantments(JsonElement value) {
        var object = ResourceDefinitions.object(value); var levels = new LinkedHashMap<ResourceLocation, Integer>();
        if (object.size() > ResourceDefinitions.MAX_CASES) throw new JsonParseException("Enchantment map exceeds limit");
        for (var entry : object.entrySet()) {
            int level = integer(entry.getValue());
            if (level < 0 || level > 255) throw new JsonParseException("Enchantment level outside native [0,255]");
            if (levels.putIfAbsent(ResourceLocation.parse(ResourceDefinitions.identifier(entry.getKey())), level) != null)
                throw new JsonParseException("Duplicate enchantment key");
        }
        return new Enchantments(levels);
    }

    private static Object enchantments(Object value) {
        if (((ItemEnchantments) value).entrySet().size() > ResourceDefinitions.MAX_CASES) return ResourceValueCache.ABSENT;
        var levels = new LinkedHashMap<ResourceLocation, Integer>();
        for (var entry : ((ItemEnchantments) value).entrySet()) {
            var key = entry.getKey().unwrapKey();
            if (key.isEmpty()) return ResourceValueCache.ABSENT;
            levels.put(key.get().location(), entry.getIntValue());
        }
        return new Enchantments(levels);
    }

    private static Object effects(JsonElement value) {
        if (!value.isJsonArray() || value.getAsJsonArray().size() > ResourceDefinitions.MAX_CASES)
            throw new JsonParseException("Expected bounded stew-effect list");
        var result = new ArrayList<Effect>();
        for (var entry : value.getAsJsonArray()) {
            var object = ResourceDefinitions.object(entry);
            ResourceDefinitions.fields(object, Set.of("id", "duration"), "stew effect");
            var id = ResourceLocation.parse(ResourceDefinitions.identifier(ResourceDefinitions.string(object, "id")));
            if (!BuiltInRegistries.MOB_EFFECT.containsKey(id)) throw new JsonParseException("Unknown stew effect: " + id);
            int duration = object.has("duration") ? integer(object.get("duration")) : 160;
            result.add(new Effect(id, duration));
        }
        return new Effects(result);
    }

    private static Object effects(Object value) {
        if (((SuspiciousStewEffects) value).effects().size() > ResourceDefinitions.MAX_CASES) return ResourceValueCache.ABSENT;
        var result = new ArrayList<Effect>();
        for (var entry : ((SuspiciousStewEffects) value).effects()) {
            var key = entry.effect().unwrapKey();
            if (key.isEmpty()) return ResourceValueCache.ABSENT;
            result.add(new Effect(key.get().location(), entry.duration()));
        }
        return new Effects(result);
    }

    private static Object variant(CustomData data) {
        // Read only; CustomData's tag and the stack remain unchanged.
        var tag = data.getUnsafe();
        if (!tag.contains("variant", 8)) return ResourceValueCache.ABSENT;
        return Objects.requireNonNullElse(ResourceLocation.tryParse(tag.getString("variant")), ResourceValueCache.ABSENT);
    }

    private static ResourceLocation key(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected variant identifier");
        return ResourceLocation.parse(ResourceDefinitions.identifier(value.getAsString()));
    }

    private static int integer(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new JsonParseException("Expected integer component value");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException failure) { throw new JsonParseException("Component integer is not exact", failure); }
    }

    private static int color(JsonElement value) {
        if (value.isJsonPrimitive()) return integer(value);
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) throw new JsonParseException("Expected RGB integer or three channels");
        int color = 0;
        for (var channel : value.getAsJsonArray()) {
            if (!channel.isJsonPrimitive() || !channel.getAsJsonPrimitive().isNumber()) throw new JsonParseException("Expected RGB channel");
            float number = channel.getAsFloat();
            if (!Float.isFinite(number) || number < 0 || number > 1) throw new JsonParseException("RGB channel outside [0,1]");
            color = color << 8 | (int) (number * 255);
        }
        return color;
    }
}
