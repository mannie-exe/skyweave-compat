package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Required authored content, assessed on the actual assembly and retained start without world reads. */
public final class VillageComposition {
    public enum Status { QUALIFIED, MISSING_REQUIRED, UNSUPPORTED_ELEMENT, PIECE_BUDGET, MISSING_CONTRACT, UNCONFIGURED, DISABLED }
    public record Identity(String template, String type, String processors, String projection) {}
    public record Requirement(int minimum, Set<Identity> alternatives) {
        public Requirement { alternatives = Set.copyOf(alternatives); }
    }
    public record Contract(Map<String, Requirement> roles) {
        public Contract { roles = Collections.unmodifiableMap(new TreeMap<>(roles)); }
    }
    public record Occurrence(Identity identity, int x, int y, int z, String rotation) {}
    private record Physical(String template, int x, int y, int z, String rotation) {}
    public record Result(Status status, Map<String, Integer> counts, Map<String, Integer> missing, int inspected) {
        public Result { counts = Map.copyOf(counts); missing = Map.copyOf(missing); }
        public boolean qualified() { return status == Status.QUALIFIED; }
    }
    record Bound(ResourceLocation profile, Contract contract, Result initial) {}
    private static final int MAX_PIECES = 2048, MAX_CHARS = 65536;
    private static final String DIRECTORY = "village_composition";
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> COMPOSED = TagKey.create(
            Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("skyweave_compat", "managed/composed_villages"));
    private static volatile Map<ResourceLocation, Contract> contracts = Map.of();
    private static volatile boolean serverStarted;
    private VillageComposition() {}

    /** Stable semantic inputs for saved generation identity, independent of map/set iteration order. */
    public static JsonObject generationInputs() { return generationInputs(contracts); }
    static JsonObject generationInputs(Map<ResourceLocation,Contract> current) {
        var result=new JsonObject();
        var identities=Comparator.comparing(Identity::template).thenComparing(Identity::type)
                .thenComparing(Identity::processors).thenComparing(Identity::projection);
        current.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(profile->{
            var roles=new JsonObject();
            profile.getValue().roles().forEach((name,requirement)->{
                var role=new JsonObject();role.addProperty("minimum",requirement.minimum());var alternatives=new com.google.gson.JsonArray();
                requirement.alternatives().stream().sorted(identities).forEach(identity->{
                    var value=new JsonObject();value.addProperty("template",identity.template());value.addProperty("type",identity.type());
                    value.addProperty("processors",identity.processors());value.addProperty("projection",identity.projection());alternatives.add(value);
                });
                role.add("alternatives",alternatives);roles.add(name,role);
            });
            result.add(profile.getKey().toString(),roles);
        });
        return result;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new Loader(event.getRegistryAccess())));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> serverStarted = true);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> { serverStarted = false; contracts = Map.of(); });
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, Contract>> {
        private final RegistryAccess registries;
        Loader(RegistryAccess registries) { this.registries = registries; }
        @Override protected Map<ResourceLocation, Contract> prepare(ResourceManager resources, ProfilerFiller profiler) {
            var files = resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
            require(files.size() <= 32, "Too many village composition contracts");
            var result = new HashMap<ResourceLocation, Contract>();
            for (var entry : files.entrySet()) {
                var file = entry.getKey();
                String path = file.getPath();
                var id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(DIRECTORY.length() + 1, path.length() - 5));
                try (var reader = entry.getValue().openAsReader()) {
                    var chars = new char[MAX_CHARS + 1]; int used = 0;
                    while (used < chars.length) {
                        int count = reader.read(chars, used, chars.length - used);
                        if (count < 0) break;
                        used += count;
                    }
                    require(used <= MAX_CHARS, "Oversized village composition contract: " + id);
                    String text = new String(chars, 0, used);
                    var contract = parse(text);
                    // Conditional structure resources may be absent with an optional provider.
                    if (registries.registryOrThrow(Registries.STRUCTURE).containsKey(id)) result.put(id, contract);
                } catch (IOException | IllegalArgumentException invalid) {
                    throw new IllegalStateException("Invalid village composition contract " + file, invalid);
                }
            }
            return Map.copyOf(result);
        }
        @Override protected void apply(Map<ResourceLocation, Contract> prepared, ResourceManager resources, ProfilerFiller profiler) {
            // Existing immutable fitted plans were made against the startup contract.
            // Do not hot-swap their policy while another chunk commits that plan.
            if (serverStarted && !contracts.equals(prepared))
                throw new IllegalStateException("Village composition changed; restart the server to rebuild generation plans");
            contracts = prepared;
        }
    }

    public static Contract parse(String text) {
        require(text.length() <= MAX_CHARS, "Oversized village composition contract");
        var root = ProfileJson.object(text); exact(root, "schema", "roles");
        require(integer(root.get("schema")) == 1, "Unsupported village composition schema");
        require(root.get("roles").isJsonObject(), "Expected required roles");
        var roles = root.getAsJsonObject("roles");
        require(roles.size() >= 1 && roles.size() <= 8, "Required role count outside bounds");
        var parsed = new TreeMap<String, Requirement>();
        int total = 0;
        for (var entry : roles.entrySet()) {
            require(entry.getKey().matches("[a-z0-9_]{1,64}") && entry.getValue().isJsonObject(), "Invalid required role");
            var role = entry.getValue().getAsJsonObject(); exact(role, "minimum", "alternatives");
            int minimum = integer(role.get("minimum"));
            require(minimum >= 1 && minimum <= 16, "Required minimum outside bounds");
            require(role.get("alternatives").isJsonArray(), "Expected qualified alternatives");
            var alternatives = role.getAsJsonArray("alternatives");
            require(alternatives.size() >= 1 && alternatives.size() <= 128 && (total += alternatives.size()) <= 256,
                    "Required alternatives outside bounds");
            var identities = new HashSet<Identity>();
            for (var value : alternatives) {
                require(value.isJsonObject(), "Expected qualified element identity");
                var identity = value.getAsJsonObject(); exact(identity, "template", "type", "processors", "projection");
                String template = resource(identity.get("template")), type = resource(identity.get("type"));
                String processors = resource(identity.get("processors")), projection = string(identity.get("projection"));
                require(Set.of("minecraft:single_pool_element", "minecraft:legacy_single_pool_element").contains(type), "Unsupported qualified element type");
                require(Set.of("rigid", "terrain_matching").contains(projection), "Unsupported qualified projection");
                require(identities.add(new Identity(template, type, processors, projection)), "Duplicate qualified alternative");
            }
            parsed.put(entry.getKey(), new Requirement(minimum, identities));
        }
        return new Contract(parsed);
    }

    private static void exact(JsonObject object, String... fields) {
        require(object.keySet().equals(Set.of(fields)), "Unexpected composition fields");
    }
    private static int integer(JsonElement value) {
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "Expected composition integer");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Expected exact composition integer", invalid); }
    }
    private static String string(JsonElement value) {
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "Expected composition text");
        return value.getAsString();
    }
    private static String resource(JsonElement value) {
        String text = string(value);
        require(text.length() <= 256 && text.contains(":"), "Invalid composition resource ID");
        var id = ResourceLocation.parse(text);
        require(id.toString().equals(text), "Composition resource IDs must be fully qualified");
        require(Arrays.stream(id.getPath().split("/", -1)).noneMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")), "Invalid composition resource path");
        return id.toString();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    /** Exact identity and physical occurrence, never a counter label or template-name role guess. */
    public static Result evaluate(Contract contract, List<Occurrence> pieces, boolean unsupported) {
        if (pieces.size() > MAX_PIECES) return new Result(Status.PIECE_BUDGET, Map.of(), Map.of(), 0);
        var index = new HashMap<Identity, List<String>>();
        var seen = new TreeMap<String, Set<Physical>>();
        contract.roles().forEach((role, requirement) -> {
            seen.put(role, new HashSet<>());
            for (var identity : requirement.alternatives()) index.computeIfAbsent(identity, key -> new ArrayList<>()).add(role);
        });
        for (var piece : pieces) {
            var physical = new Physical(piece.identity().template(), piece.x(), piece.y(), piece.z(), piece.rotation());
            for (var role : index.getOrDefault(piece.identity(), List.of())) seen.get(role).add(physical);
        }
        var counts = new TreeMap<String, Integer>(); var missing = new TreeMap<String, Integer>();
        contract.roles().forEach((role, requirement) -> {
            int count = seen.get(role).size(); counts.put(role, count);
            if (count < requirement.minimum()) missing.put(role, requirement.minimum() - count);
        });
        return new Result(missing.isEmpty() ? Status.QUALIFIED : unsupported ? Status.UNSUPPORTED_ELEMENT : Status.MISSING_REQUIRED,
                counts, missing, pieces.size());
    }

    private static Result inspect(Contract contract, List<StructurePiece> pieces) {
        if (pieces.size() > MAX_PIECES) return new Result(Status.PIECE_BUDGET, Map.of(), Map.of(), 0);
        var occurrences = new ArrayList<Occurrence>(); boolean unsupported = false;
        for (var piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pool)) { unsupported = true; continue; }
            StructurePoolElement element = pool.getElement();
            for (int depth = 0; element instanceof VillageDelegate delegate && depth < 8; depth++) element = delegate.skyweave$delegate();
            // The authored catalog admits complete Single/Legacy elements only.
            // Known non-single choices are preserved but cannot match that identity.
            if (element == EmptyPoolElement.INSTANCE || element instanceof FeaturePoolElement || element instanceof ListPoolElement) continue;
            if (!(element instanceof SinglePoolElement single)
                    || single.getClass() != SinglePoolElement.class && single.getClass() != LegacySinglePoolElement.class) {
                unsupported = true; continue;
            }
            var access = (VillageTemplateAccess)single;
            var template = access.skyweave$template().left();
            var processor = access.skyweave$processors().unwrapKey();
            // Inline values are a different, known identity from the catalog's named holders.
            if (template.isEmpty() || processor.isEmpty()) continue;
            var identity = new Identity(template.get().toString(), single instanceof LegacySinglePoolElement
                    ? "minecraft:legacy_single_pool_element" : "minecraft:single_pool_element",
                    processor.get().location().toString(), single.getProjection().getSerializedName());
            var at = pool.getPosition();
            occurrences.add(new Occurrence(identity, at.getX(), at.getY(), at.getZ(), pool.getRotation().name()));
        }
        var result = evaluate(contract, occurrences, unsupported);
        return new Result(result.status(), result.counts(), result.missing(), pieces.size());
    }

    public static Result assess(ResourceLocation profile, List<StructurePiece> pieces) {
        if (!OptionalIntegrations.enabled(Feature.GROUND_SUPPORT)) return new Result(Status.DISABLED, Map.of(), Map.of(), 0);
        var contract = contracts.get(profile);
        return contract == null ? new Result(Status.UNCONFIGURED, Map.of(), Map.of(), 0) : inspect(contract, pieces);
    }

    static Bound bind(StructureStart original, RegistryAccess registries) {
        if (!OptionalIntegrations.enabled(Feature.GROUND_SUPPORT)) return null;
        var registry = registries.registryOrThrow(Registries.STRUCTURE);
        var id = registry.getKey(original.getStructure()); if (id == null) return null;
        var contract = contracts.get(id);
        if (contract == null && !registry.getHolder(id).orElseThrow().is(COMPOSED)) return null;
        var initial = contract == null ? new Result(Status.MISSING_CONTRACT, Map.of(), Map.of(), 0) : inspect(contract, original.getPieces());
        return new Bound(id, contract, initial);
    }

    static boolean accept(Bound bound, List<StructurePiece> pieces, boolean initial) {
        if (bound == null) return true;
        var result = initial || bound.contract() == null ? bound.initial() : inspect(bound.contract(), pieces);
        VillageDecisionTrace.emit(bound.profile(), initial ? "composition_assembled" : "composition_retained", result.status().name(),
                Map.of("counts", result.counts(), "missing", result.missing(), "inspected", result.inspected()));
        return result.qualified();
    }
}
