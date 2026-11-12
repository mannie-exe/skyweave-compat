package design.inherent.skyweavecompat.configuration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded optimistic snapshots. Coordinates our writers, not noncooperating external editors. */
public final class ConfigFile {
    public static final int LIMIT = 131072;
    private static final ConcurrentHashMap<Path, Object> WRITERS = new ConcurrentHashMap<>();
    private final Path directory, path;

    public static final class ChangedOnDisk extends IOException {
        public ChangedOnDisk() { super("The file changed outside this editor. Reload to review it."); }
    }

    public ConfigFile(Path directory, String filename) throws IOException {
        if (!filename.matches("[a-zA-Z0-9_.-]{1,128}") || filename.equals(".") || filename.equals(".."))
            throw new IOException("Expected a catalogued configuration filename");
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Configuration directory must be a regular local directory");
        this.directory = directory.toRealPath();
        this.path = this.directory.resolve(filename);
    }

    public Path path() { return path; }

    /** Startup and screen-open consumers share the editor's regular-file and byte limits. */
    public static String read(Path path) throws IOException {
        if(path.getFileName()==null)throw new IOException("Expected a configuration filename");
        return new ConfigFile(path.toAbsolutePath().getParent(),path.getFileName().toString()).read();
    }

    public String read() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IOException("Configuration must be a regular local file");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(LIMIT + 1); }
        if (bytes.length > LIMIT) throw new IOException("Configuration exceeds 128 KiB");
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { throw new IOException("Configuration is not valid UTF-8", invalid); }
    }

    public void check(String baseline) throws IOException {
        if (!read().equals(baseline)) throw new ChangedOnDisk();
    }

    public String write(String baseline, String candidate) throws IOException {
        if (candidate.getBytes(StandardCharsets.UTF_8).length > LIMIT)
            throw new IOException("Configuration exceeds 128 KiB");
        synchronized (WRITERS.computeIfAbsent(path, ignored -> new Object())) {
            check(baseline);
            if (!Files.isWritable(path)) throw new IOException("Configuration is not writable");
            Path temporary = Files.createTempFile(directory, ".compat-settings-", ".tmp");
            try {
                var attributes = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes != null) Files.setPosixFilePermissions(temporary, attributes.readAttributes().permissions());
                Files.writeString(temporary, candidate, StandardCharsets.UTF_8);
                check(baseline);
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                String actual = read();
                if (!actual.equals(candidate)) throw new IOException("Written file changed before readback. Reload to review it.");
                return actual;
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    /** Registered owners keep their serializer and reload events; coordinate only this handoff. */
    public String throughOwner(String baseline, SettingsCommit.Operation save) throws Exception {
        synchronized (WRITERS.computeIfAbsent(path, ignored -> new Object())) {
            check(baseline);
            if (!Files.isWritable(path)) throw new IOException("Configuration is not writable");
            save.run();
            return read();
        }
    }
}
