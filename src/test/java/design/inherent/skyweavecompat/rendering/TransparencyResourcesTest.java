package design.inherent.skyweavecompat.rendering;

import design.inherent.skyweavecompat.client.TransparencyProfiles;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransparencyResourcesTest {
    @Test void unreadableResourceWithoutAnExceptionMessageRetainsOriginalRendering() throws Exception {
        var id = ResourceLocation.parse("fixture:transparency/unreadable.json");
        var pack = (PackResources) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, args) -> method.getName().equals("packId") ? "fixture" : null);
        var resource = new Resource(pack, () -> { throw new IOException(); });
        var manager = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) return Map.of(id, resource);
                    throw new AssertionError("Unexpected resource lookup: " + method);
                });
        var before = TransparencyProfiles.current();
        try {
            var result = assertDoesNotThrow(() -> TransparencyProfiles.reload(manager));
            assertTrue(result.rules().isEmpty());
            assertEquals("IOException", result.diagnostics().get(id));
        } finally {
            var current = TransparencyProfiles.class.getDeclaredField("current"); current.setAccessible(true);
            current.set(null, before);
        }
    }
}
