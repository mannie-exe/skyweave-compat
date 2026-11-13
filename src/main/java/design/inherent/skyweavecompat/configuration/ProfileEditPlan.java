package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.terrain.GenerationConfiguration;
import design.inherent.skyweavecompat.terrain.ShapeConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The declared generation/shape dependency, not a general file transaction or runtime mutation. */
public final class ProfileEditPlan {
    private static final List<String> PAIR = List.of(GenerationConfiguration.FILE, ShapeConfiguration.FILE);

    public static SettingsCommit.Prepared prepare(Path directory, List<SettingsCommit.Write> writes) throws IOException {
        writes = List.copyOf(writes);
        // Reject duplicate owners before deriving the shared read-set.
        var independent = SettingsCommit.prepare(writes);
        var dependent = writes.stream().filter(write -> PAIR.contains(write.filename())).toList();
        if (dependent.isEmpty()) return independent;
        var files = new LinkedHashMap<String, ConfigFile>();
        var expected = new LinkedHashMap<String, String>();
        for (String name : PAIR) {
            var file = new ConfigFile(directory, name);
            files.put(name, file); expected.put(name, file.read());
        }
        var target = new LinkedHashMap<>(expected);
        for (var write : dependent) {
            if (write.text() == null) throw new IllegalArgumentException("Profile writes require a frozen candidate");
            if (!expected.get(write.filename()).equals(write.text().baseline())) throw new ConfigFile.ChangedOnDisk();
            target.put(write.filename(), write.text().candidate());
        }
        validate(target);
        List<SettingsCommit.Write> order = dependent;
        if (!safePrefixes(expected, order)) {
            order = dependent.reversed();
            if (!safePrefixes(expected, order))
                throw new IllegalArgumentException("Generation and shape changes have no safe partial-save order");
        }
        var ordered = new ArrayList<SettingsCommit.Write>();
        boolean added = false;
        for (var write : writes) {
            if (!PAIR.contains(write.filename())) ordered.add(write);
            else if (!added) { ordered.addAll(order); added = true; }
        }
        var guarded = new ArrayList<SettingsCommit.Write>();
        for (var write : ordered) guarded.add(new SettingsCommit.Write(write.filename(), write.text(), () -> {
            for (var entry : files.entrySet()) entry.getValue().check(expected.get(entry.getKey()));
            write.check();
        }, () -> {
            write.save();
            if (PAIR.contains(write.filename())) {
                files.get(write.filename()).check(write.text().candidate());
                expected.put(write.filename(), write.text().candidate());
            }
        }, write::savedObservation, write::failureObservation));
        return SettingsCommit.prepare(guarded);
    }

    private static boolean safePrefixes(Map<String, String> before, List<SettingsCommit.Write> writes) {
        var prefix = new LinkedHashMap<>(before);
        for (var write : writes) {
            prefix.put(write.filename(), write.text().candidate());
            try { validate(prefix); } catch (IllegalArgumentException invalid) { return false; }
        }
        return true;
    }
    private static void validate(Map<String, String> values) {
        var shapes = ShapeConfiguration.parse(values.get(ShapeConfiguration.FILE));
        ProfileJson.generation(values.get(GenerationConfiguration.FILE)).validateAgainst(shapes);
    }
    private ProfileEditPlan() {}
}
