package design.inherent.skyweavecompat.client.model;

import com.google.gson.JsonParseException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceJsonTest {
    @Test void refusesAmbiguousOrUnboundedInputBeforeTreeCompilation() throws IOException {
        assertThrows(JsonParseException.class, () -> read("{\"model\":{},\"model\":{}}"));
        assertThrows(JsonParseException.class, () -> read("[".repeat(66) + "0" + "]".repeat(66)));
        assertThrows(JsonParseException.class, () -> read("{\"x\":\"" + "x".repeat(ResourceDefinitions.MAX_BYTES) + "\"}"));
        assertThrows(IOException.class, () -> read("{} {}"));
        assertEquals("模型", read("{\"name\":\"模型\"}").get("name").getAsString());
    }
    private static com.google.gson.JsonObject read(String input) throws IOException {
        return ResourceJson.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }
}
