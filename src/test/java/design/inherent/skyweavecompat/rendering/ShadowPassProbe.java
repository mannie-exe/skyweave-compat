package design.inherent.skyweavecompat.rendering;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the handler extracted from the real transformed target with a controlled Iris API. */
final class ShadowPassProbe {
    public static final class Api {
        static boolean shadow;
        public static Api getInstance() { return new Api(); }
        public boolean isRenderingShadowPass() { return shadow; }
    }

    static void verify(ClassNode target) throws Exception {
        verify(target, "skyweave$skipShadowComposite");
    }
    static void verify(ClassNode target, String handler) throws Exception {
        var originalMethod = target.methods.stream().filter(m -> m.name.endsWith(handler)).findFirst().orElseThrow();
        var method = new MethodNode(Opcodes.ASM9, originalMethod.access, originalMethod.name, originalMethod.desc, null, null);
        originalMethod.accept(method);
        boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
        method.name = "check";
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        method.visibleAnnotations = null;
        method.invisibleAnnotations = null;
        String original = "net/irisshaders/iris/api/v0/IrisApi";
        String stub = Api.class.getName().replace('.', '/');
        for (var instruction : method.instructions.toArray()) {
            if (instruction instanceof FrameNode) method.instructions.remove(instruction);
            if (instance && instruction instanceof VarInsnNode variable) {
                assertTrue(variable.var > 0, "the guard must not depend on its renderer instance");
                variable.var--;
            }
            if (instruction instanceof MethodInsnNode call && call.owner.equals(original)) {
                call.owner = stub;
                call.desc = call.desc.replace(original, stub);
                call.itf = false;
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE) call.setOpcode(Opcodes.INVOKEVIRTUAL);
            }
        }
        var generated = new ClassNode();
        generated.version = Opcodes.V21;
        generated.access = Opcodes.ACC_PUBLIC;
        generated.name = "ExtractedShadowGuard";
        generated.superName = "java/lang/Object";
        generated.methods.add(method);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        generated.accept(writer);
        class Loader extends ClassLoader {
            Class<?> define(byte[] bytes) { return defineClass("ExtractedShadowGuard", bytes, 0, bytes.length); }
        }
        var check = new Loader().define(writer.toByteArray()).getMethod("check", CallbackInfo.class);
        var preferences = design.inherent.skyweavecompat.client.RenderingPreferences.SPEC;
        var values = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        preferences.correct(values);
        // The pinned loader seals ILoadedConfig; use its real in-memory holder, never a fake spec reader.
        var loadedType = Class.forName("net.neoforged.fml.config.LoadedConfig");
        var constructor = loadedType.getDeclaredConstructor(com.electronwill.nightconfig.core.CommentedConfig.class,
                java.nio.file.Path.class, net.neoforged.fml.config.ModConfig.class);
        constructor.setAccessible(true);
        preferences.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig) constructor.newInstance(values, null, null));
        try {
            for (boolean textPolicy : new boolean[]{true, false}) for (boolean nametagPolicy : new boolean[]{false, true}) {
                design.inherent.skyweavecompat.client.RenderingPreferences.TEXT_SHADOW.set(textPolicy);
                design.inherent.skyweavecompat.client.RenderingPreferences.NAMETAG_SHADOW.set(nametagPolicy);
                boolean policy = handler.equals("skyweave$skipTextDisplayShadow") ? textPolicy
                        : handler.equals("skyweave$skipNametagShadow") ? nametagPolicy : true;
                for (boolean shadow : new boolean[]{false, true, false}) {
                    Api.shadow = shadow;
                    var callback = new CallbackInfo("world-render", true);
                    check.invoke(null, callback);
                    assertEquals(policy && shadow, callback.isCancelled(), "independent policy in shadow pass: " + handler);
                }
            }
        } finally { preferences.acceptConfig(null); }
    }
}
