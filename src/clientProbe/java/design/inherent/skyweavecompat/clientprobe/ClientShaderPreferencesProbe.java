package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.uniforms.CommonUniforms;

/** Captures the suppliers registered by the actual injected Iris handler, not a replacement policy. */
final class ClientShaderPreferencesProbe {
    static JsonObject observe() throws Exception {
        var suppliers = new LinkedHashMap<String, IntSupplier>();
        var holder = (UniformHolder) Proxy.newProxyInstance(UniformHolder.class.getClassLoader(), new Class<?>[]{UniformHolder.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("uniform1i")) suppliers.put((String) args[1], (IntSupplier) args[2]);
                    return proxy;
                });
        var injected = java.util.Arrays.stream(CommonUniforms.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("environment") && method.getParameterCount() == 4).findFirst().orElseThrow();
        injected.setAccessible(true);
        injected.invoke(null, holder, null, null, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("environment-review", false));
        var result = new JsonObject();
        result.addProperty("world", suppliers.get("skyweaveWorld").getAsInt());
        result.addProperty("voidFog", suppliers.get("skyweaveVoidVisuals").getAsInt());
        result.addProperty("registered", suppliers.size());
        return result;
    }
}
