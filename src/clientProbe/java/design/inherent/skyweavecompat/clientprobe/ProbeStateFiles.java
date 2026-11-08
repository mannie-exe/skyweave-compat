package design.inherent.skyweavecompat.clientprobe;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publish a complete probe response, so external readers never observe a truncated frame. */
final class ProbeStateFiles {
    static void write(Path target, String value) throws IOException {
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, value);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private ProbeStateFiles() {}
}
