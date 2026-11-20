package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.Set;
import java.util.function.*;
import net.minecraft.resources.ResourceLocation;
import timmychips.modefiteitemdefinitions.property.registry.*;
import timmychips.modefiteitemdefinitions.property.type.ItemModelTypes;
import timmychips.modefiteitemdefinitions.property.type.codec.*;

/** Compile property metadata once; use the pinned mod's public value interfaces for unchanged properties. */
final class NativeResourceValues implements ResourceDefinitions.Values<ResourceComponents.Input> {
    private static final Set<String> SELECT = Set.of("minecraft:block_state", "minecraft:charge_type", "minecraft:context_dimension",
            "minecraft:context_entity_type", "minecraft:custom_model_data", "minecraft:display_context", "minecraft:main_hand", "minecraft:trim_material");
    private static final Set<String> CONDITIONS = Set.of("minecraft:broken", "minecraft:carried", "minecraft:custom_model_data",
            "minecraft:damaged", "minecraft:extended_view", "minecraft:fishing_rod/cast", "minecraft:has_component",
            "minecraft:keybind_down", "minecraft:selected", "minecraft:using_item", "minecraft:view_entity", "modefite:hovered_item", "modefite:submerged");
    private static final Set<String> RANGES = Set.of("minecraft:bundle/fullness", "minecraft:compass", "minecraft:cooldown", "minecraft:count",
            "minecraft:crossbow/pull", "minecraft:damage", "minecraft:time", "minecraft:use_cycle", "minecraft:use_duration", "minecraft:custom_model_data");
    private final ResourceComponents components;
    private final ResourcePredicates predicates;

    NativeResourceValues(ResourceValueCache cache) { components = new ResourceComponents(cache); predicates = new ResourcePredicates(cache); }
    NativeResourceValues(ResourceValueCache cache, java.util.function.Supplier<net.minecraft.core.HolderLookup.Provider> registries) {
        components = new ResourceComponents(cache); predicates = new ResourcePredicates(cache, registries);
    }

    @Override public ResourceDefinitions.Selector<ResourceComponents.Input> select(String property, JsonObject definition) {
        fields(definition, Set.of("type", "property", "cases", "fallback"), switch (property) {
            case "minecraft:component" -> Set.of("component");
            case "minecraft:block_state" -> Set.of("block_state_property");
            case "minecraft:charge_type" -> Set.of("ignore_default", "ignore_unknown");
            default -> Set.of();
        }, property);
        if (property.equals("minecraft:component"))
            return components.selector(ResourceDefinitions.identifier(ResourceDefinitions.string(definition, "component")));
        if (!SELECT.contains(property)) throw new JsonParseException("Unsupported select property: " + property);
        var metadata = stub(definition, SelectDefinition.Definition.class);
        var id = ResourceLocation.parse(property);
        return new ResourceDefinitions.Selector<>() {
            public Object decode(JsonElement value) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected string property case: " + property);
                String text = value.getAsString();
                if (Set.of("minecraft:context_dimension", "minecraft:context_entity_type", "minecraft:trim_material").contains(property))
                    return ResourceDefinitions.identifier(text);
                // Future/extension contexts (for example on_shelf) remain unreachable cases on 1.21.1.
                // Retain the authored fallback and the native contexts in the same definition.
                if (text.isEmpty() || text.length() > 512) throw new JsonParseException("Invalid property case: " + property);
                if (property.equals("minecraft:main_hand") && !text.equals("left") && !text.equals("right"))
                    throw new JsonParseException("Unknown main-hand value: " + text);
                return text;
            }
            public Object current(ResourceComponents.Input input) {
                return SelectPropertyRegistry.resolve(id, input.stack(), input.entity(), input.display(), metadata);
            }
        };
    }

    @Override public Predicate<ResourceComponents.Input> condition(String property, JsonObject definition) {
        fields(definition, Set.of("type", "property", "on_true", "on_false"), switch (property) {
            case "minecraft:component" -> Set.of("predicate", "value");
            case "minecraft:has_component" -> Set.of("component", "ignore_default");
            case "minecraft:keybind_down" -> Set.of("keybind");
            case "modefite:submerged" -> Set.of("fluid");
            default -> Set.of();
        }, property);
        if (property.equals("minecraft:has_component")) {
            var id = ResourceLocation.parse(ResourceDefinitions.identifier(ResourceDefinitions.string(definition, "component")));
            var type = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
            if (type == null) throw new JsonParseException("Unknown data component: " + id);
            boolean changed = ResourceDefinitions.bool(definition, "ignore_default", false);
            return input -> input.stack().has(type) && (!changed || input.stack().getComponentsPatch().get(type) != null);
        }
        if (property.equals("minecraft:component")) {
            var id = ResourceLocation.parse(ResourceDefinitions.identifier(ResourceDefinitions.string(definition, "predicate")));
            return predicates.compile(id, ResourceDefinitions.required(definition, "value"));
        }
        if (!CONDITIONS.contains(property)) throw new JsonParseException("Unsupported condition property: " + property);
        var metadata = stub(definition, ConditionDefinition.class);
        var id = ResourceLocation.parse(property);
        return input -> ConditionPropertyRegistry.resolve(id, input.stack(), input.entity(), metadata);
    }

    @Override public ToDoubleFunction<ResourceComponents.Input> range(String property, JsonObject definition) {
        fields(definition, Set.of("type", "property", "entries", "fallback", "scale"), switch (property) {
            case "minecraft:compass" -> Set.of("target", "wobble");
            case "minecraft:time" -> Set.of("source", "wobble");
            case "minecraft:count", "minecraft:damage" -> Set.of("normalize");
            case "minecraft:use_cycle" -> Set.of("period");
            case "minecraft:use_duration" -> Set.of("remaining");
            default -> Set.of();
        }, property);
        if (!RANGES.contains(property)) throw new JsonParseException("Unsupported range property: " + property);
        if (definition.has("period")) {
            float period = ResourceDefinitions.floating(definition, "period", 1);
            if (period <= 0) throw new JsonParseException("Use-cycle period must be positive");
        }
        var metadata = stub(definition, RangeDispatchDefinition.Definition.class);
        var id = ResourceLocation.parse(property);
        // The resource program applies scale once, after the unscaled property value.
        return input -> RangePropertyRegistry.resolve(id, input.stack(), input.entity(), metadata);
    }

    @Override public ToIntFunction<ResourceComponents.Input> tint(String type, JsonObject definition) { return components.tint(type, definition); }

    private static <T extends ItemModelDefinition> T stub(JsonObject definition, Class<T> type) {
        var copy = definition.deepCopy();
        // The published port's dispatch compares these identifiers without applying the default namespace.
        copy.addProperty("type", ResourceDefinitions.identifier(ResourceDefinitions.string(copy, "type")));
        var empty = new JsonObject(); empty.addProperty("type", "minecraft:empty");
        if (type == SelectDefinition.Definition.class) { copy.add("cases", new JsonArray()); copy.add("fallback", empty); }
        else if (type == RangeDispatchDefinition.Definition.class) { copy.add("entries", new JsonArray()); copy.add("fallback", empty); }
        else { copy.add("on_true", empty); copy.add("on_false", empty); }
        var result = ItemModelTypes.CODEC.parse(JsonOps.INSTANCE, copy);
        return type.cast(result.result().orElseThrow(() -> new JsonParseException(result.error().orElseThrow().message())));
    }
    private static void fields(JsonObject value, Set<String> common, Set<String> property, String owner) {
        var allowed = new java.util.HashSet<>(common); allowed.addAll(property);
        ResourceDefinitions.fields(value, allowed, owner + " property");
    }
}
