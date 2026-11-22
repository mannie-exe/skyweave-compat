package design.inherent.skyweavecompat.client.input;

import design.inherent.skyweavecompat.mixin.client.input.MovesEmoteAccess;
import design.inherent.skyweavecompat.mixin.client.input.MovesKeyReleaseAccess;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/** Resource registration publishes active identities once; native input lookup performs no catalog scan. */
public final class MovesBindingActivity {
    private static Set<KeyMapping> active = Set.of();
    private MovesBindingActivity() {}

    public static void publish(Collection<?> known, Collection<?> selected) {
        active = selected.stream().map(value -> ((MovesEmoteAccess) value).skyweave$mapping()).collect(Collectors.toUnmodifiableSet());
        for (var value : known) {
            var mapping = ((MovesEmoteAccess) value).skyweave$mapping();
            if (!(mapping.getKeyConflictContext() instanceof EmoteContext))
                mapping.setKeyConflictContext(new EmoteContext(mapping, mapping.getKeyConflictContext()));
            if (!active.contains(mapping)) ((MovesKeyReleaseAccess) mapping).skyweave$releaseMovesBinding();
        }
    }

    private record EmoteContext(KeyMapping mapping, IKeyConflictContext original) implements IKeyConflictContext {
        public boolean isActive() { return active.contains(mapping) && original.isActive(); }
        public boolean conflicts(IKeyConflictContext other) {
            return original.conflicts(other instanceof EmoteContext context ? context.original : other);
        }
    }
}
