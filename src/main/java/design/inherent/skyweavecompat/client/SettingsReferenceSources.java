package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientFileSettings;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.ClientFormOptions;
import design.inherent.reform.minecraft.reference.NativeReferences;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.reform.reference.ReferenceActions;
import design.inherent.reform.reference.ReferenceIndex;
import design.inherent.reform.reference.ReferencePage;
import design.inherent.reform.reference.ReferenceStamp;
import design.inherent.reform.value.ValueTypes;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import design.inherent.skyweavecompat.configuration.SettingsReferences;
import design.inherent.skyweavecompat.configuration.SettingsReferences.Source;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;

/** Advisory selectors share the actual file owner; native registry reads occur only on capture. */
final class SettingsReferenceSources<T> {
    private static final NativeReferences.Limits LIMITS = NativeReferences.Limits.STANDARD;
    private final ClientFileSettings<T> owner;
    private final Set<Source> kinds;
    private final Map<Source, ReferenceActions<String, List<String>>> actions = new EnumMap<>(Source.class);
    private long revision;

    SettingsReferenceSources(ClientFileSettings<T> owner, JsonSettings.Definition definition) {
        this.owner = owner;
        kinds = definition.fields().stream().map(field -> SettingsReferences.source(definition.filename(), field.key()))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (kinds.isEmpty()) return;
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.TagsUpdatedEvent event) ->
                Minecraft.getInstance().execute(() -> refresh(false)));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> refresh(true));
    }

    ClientForm form(Component title, FormPresentation presentation) {
        var base = owner.form(title, presentation);
        return ClientForm.of(base.document(), title, base::availability,
                parent -> owner.screen(parent, title, new ClientFormOptions<T>(presentation, references())), Optional.of(base::retry));
    }

    private Map<DocumentId, ReferenceActions<String, List<String>>> references() {
        for (var source : kinds) {
            var previous = actions.get(source);
            if (previous == null || !previous.isOpen()) actions.put(source, owner.references(source.id(), ValueTypes.text(512),
                    NativeReferences.valueType(LIMITS).snapshotPolicy(), LIMITS.index()));
        }
        refresh(false);
        var result = new java.util.LinkedHashMap<DocumentId, ReferenceActions<String, List<String>>>();
        actions.forEach((source, action) -> result.put(source.id(), action));
        return Map.copyOf(result);
    }

    private void refresh(boolean disconnected) {
        long selected = ++revision;
        String observed = "client-registry-" + selected;
        actions.forEach((source, action) -> {
            if (!action.isOpen()) return;
            // A failed or superseded capture leaves the library's explicit unavailable source.
            // It never publishes the successfully scanned prefix of an over-budget registry.
            action.bindCaptured(observed, (context, stamp) -> capture(source, stamp, disconnected), Capture::build)
                    .whenComplete((stamp, failure) -> {
                        if (failure == null) return;
                        Minecraft.getInstance().execute(() -> {
                            if (revision != selected || !action.isOpen()) return;
                            String reason = "Cannot inspect the complete reference source: " + failure.getMessage();
                            if (reason.length() > 1024) reason = reason.substring(0, 1024);
                            String retained = reason;
                            action.bind(observed + "-unavailable", replacement -> new Capture(replacement,
                                    ReferencePage.Availability.UNAVAILABLE_CONTEXT, retained, List.of()).build());
                        });
                    });
        });
    }

    private record Capture(ReferenceStamp stamp, ReferencePage.Availability availability, String reason,
            List<ReferenceIndex.Entry<String, List<String>>> entries) {
        ReferenceIndex<String, List<String>> build() {
            return new ReferenceIndex<>(stamp, ValueTypes.text(512), NativeReferences.valueType(LIMITS).snapshotPolicy(),
                    LIMITS.index(), LIMITS.snapshots(), availability, reason, entries);
        }
    }

    private static Capture capture(Source source, ReferenceStamp stamp, boolean disconnected) {
        if (source == Source.MAPS)
            return new Capture(stamp, ReferencePage.Availability.UNAVAILABLE_CONTEXT,
                    "BlueMap map identities belong to the dedicated server. This local editor has no admitted server map registry.", List.of());
        var level = Minecraft.getInstance().level;
        if (disconnected || level == null)
            return new Capture(stamp, ReferencePage.Availability.UNAVAILABLE_CONTEXT,
                    "Join a world to inspect its current registry and tag matches. Authored references remain editable.", List.of());
        Registry<?> registry = source == Source.BLOCKS || source == Source.BLOCK_SELECTORS ? BuiltInRegistries.BLOCK
                : level.registryAccess().registryOrThrow(Registries.BIOME);
        var entries = new ArrayList<ReferenceIndex.Entry<String, List<String>>>();
        if (source != Source.BIOME_TAGS) for (var id : registry.keySet()) {
            requireCapacity(entries.size());
            String key = id.toString();
            entries.add(new ReferenceIndex.Entry<>(key, List.of(key), key, key, Map.of("kind", "registry entry", "matches", "1")));
        }
        if (source == Source.BLOCK_SELECTORS || source == Source.BIOME_TAGS) {
            int members = 0;
            var tags = registry.getTags().iterator();
            while (tags.hasNext()) {
                var tag = tags.next();
                requireCapacity(entries.size());
                if (tag.getSecond().size() > LIMITS.maximumTagMembers())
                    throw new IllegalArgumentException("A registry tag exceeds the complete reference-member budget");
                var values = new ArrayList<String>();
                for (var holder : tag.getSecond()) {
                    if (++members > LIMITS.maximumTotalMembers())
                        throw new IllegalArgumentException("Registry tags exceed the complete reference-member budget");
                    values.add(holder.unwrapKey().orElseThrow().location().toString());
                }
                String key = (source == Source.BLOCK_SELECTORS ? "#" : "") + tag.getFirst().location();
                entries.add(new ReferenceIndex.Entry<>(key, List.copyOf(values), key, key,
                        Map.of("kind", "tag", "matches", Integer.toString(values.size()))));
            }
        }
        return new Capture(stamp, ReferencePage.Availability.READY, "", List.copyOf(entries));
    }

    private static void requireCapacity(int entries) {
        if (entries >= LIMITS.index().maximumEntries())
            throw new IllegalArgumentException("Registry exceeds the complete reference-entry budget");
    }
}
