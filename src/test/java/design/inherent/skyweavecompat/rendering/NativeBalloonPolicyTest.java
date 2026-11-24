/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.rendering;

import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the released admission prefix, with controlled inputs and an observed render-context boundary. */
final class NativeBalloonPolicyTest {
    public static final class Flag {
        public Object get() { return Api.enabled; }
    }
    public static final class Config {
        public final Flag enableHotAirRendering = new Flag();
    }
    public static final class Api {
        public static boolean iris, enabled, active;
        static int shaderReads, releases, admitted;
        public static boolean shaderActive() { shaderReads++; return active; }
        public static Config config() { return new Config(); }
        public static void free() { releases++; }
        public static void renderBoundary() { admitted++; }
    }

    @Test void nativeHotAirPolicyReplacesShadowGuardAndPreservesOrdinaryAdmission() throws Exception {
        String target = "dev/eriksonn/aeronautics/content/blocks/hot_air/balloon/effect/ClientBalloonEffectRenderer";
        var nativeClass = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(target + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(nativeClass, 0);
        }
        var original = nativeClass.methods.stream().filter(m -> m.name.equals("onRenderLevelStage")).findFirst().orElseThrow();
        String stageName = "foundry/veil/api/event/VeilRenderLevelStageEvent$Stage";
        var method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "admit", "(L" + stageName + ";)V", null, null);
        var labels = new HashMap<LabelNode, LabelNode>();
        for (var node : original.instructions) if (node instanceof LabelNode label) labels.put(label, new LabelNode());
        var included = new HashSet<LabelNode>();
        String api = Api.class.getName().replace('.', '/'), config = Config.class.getName().replace('.', '/'), flag = Flag.class.getName().replace('.', '/');
        boolean boundary = false, configRead = false, irisRead = false, cleanup = false;
        for (var instruction : original.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/Minecraft") && call.name.equals("getInstance")) {
                boundary = true; method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, api, "renderBoundary", "()V", false));
                method.instructions.add(new InsnNode(Opcodes.RETURN)); break;
            }
            if (instruction instanceof FrameNode || instruction instanceof LineNumberNode) continue;
            var copy = instruction.clone(labels);
            if (copy instanceof LabelNode label) included.add(label);
            if (copy instanceof FieldInsnNode field) {
                if (field.owner.equals("foundry/veil/Veil") && field.name.equals("IRIS")) { field.owner = api; field.name = "iris"; }
                else if (field.owner.equals("dev/eriksonn/aeronautics/config/client/AeroClient") && field.name.equals("enableHotAirRendering")) {
                    field.owner = config; field.desc = "L" + flag + ";";
                } else assertEquals(stageName, field.owner, "Unexpected native prefix field");
            }
            if (copy instanceof MethodInsnNode call) {
                if (call.owner.equals("net/irisshaders/iris/Iris") && call.name.equals("isPackInUseQuick")) {
                    irisRead = true; call.owner = api; call.name = "shaderActive";
                } else if (call.owner.equals("dev/eriksonn/aeronautics/config/AeroConfig") && call.name.equals("client")) {
                    configRead = true; call.owner = api; call.name = "config"; call.desc = "()L" + config + ";"; call.itf = false;
                } else if (call.owner.equals("net/createmod/catnip/config/ConfigBase$ConfigBool") && call.name.equals("get")) call.owner = flag;
                else if (call.owner.equals("java/lang/Boolean") && call.name.equals("booleanValue") && call.desc.equals("()Z")) { /* retain native unboxing */ }
                else if (call.owner.equals(target) && call.name.equals("freeFbo")) { cleanup = true; call.owner = api; call.name = "free"; }
                else fail("Unexpected native prefix call: " + call.owner + "." + call.name);
            }
            method.instructions.add(copy);
        }
        assertTrue(boundary && configRead && irisRead && cleanup, "Qualify the actual released policy and cleanup, not an empty prefix");
        for (var instruction : method.instructions) if (instruction instanceof JumpInsnNode branch)
            assertTrue(included.contains(branch.label), "Native decision jumps beyond the observed prefix");
        var generated = new ClassNode(); generated.version = Opcodes.V21; generated.access = Opcodes.ACC_PUBLIC;
        generated.name = "NativeBalloonAdmission"; generated.superName = "java/lang/Object"; generated.methods.add(method);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); generated.accept(writer);
        class Loader extends ClassLoader {
            Class<?> define(byte[] bytes) { return defineClass("NativeBalloonAdmission", bytes, 0, bytes.length); }
        }
        var stageType = Class.forName(stageName.replace('/', '.'));
        var invoke = new Loader().define(writer.toByteArray()).getMethod("admit", stageType);
        for (Object stage : stageType.getEnumConstants()) for (boolean iris : new boolean[]{false, true})
                for (boolean active : new boolean[]{false, true}) for (boolean enabled : new boolean[]{false, true}) {
            Api.iris = iris; Api.active = active; Api.enabled = enabled; Api.shaderReads = Api.releases = Api.admitted = 0;
            invoke.invoke(null, stage);
            boolean phase = ((Enum<?>)stage).name().equals("AFTER_SOLID_BLOCKS");
            boolean admitted = phase && enabled && !(iris && active);
            assertEquals(admitted ? 1 : 0, Api.admitted, "Native ordinary/render-policy admission");
            assertEquals(phase && !admitted ? 1 : 0, Api.releases, "Native disabled/shader path calls framebuffer cleanup");
            assertEquals(phase && iris ? 1 : 0, Api.shaderReads, "Iris lookup stays behind its native availability guard");
        }
    }
}
