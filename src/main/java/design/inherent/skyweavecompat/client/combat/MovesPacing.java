package design.inherent.skyweavecompat.client.combat;

import design.inherent.skyweavecompat.combat.AttackPacing;
import design.inherent.skyweavecompat.combat.AttackSnapshot;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import punchymoves.client.animation.AnimationLibrary;

/** The pinned private clock has no public copy API. Resolve its access once, after optional admission. */
public final class MovesPacing {
    private static final String LAYER = "punchymoves.client.animation.LocomotionController$OneShotLayer";
    private static final class Access {
        static final MethodHandle CLIP, TIME, FINISHED, MODIFIER, CONDITION, TIMER, SWORD, TOOL, PUNCH;
        static {
            try {
                Class<?> layer = Class.forName(LAYER);
                Class<?> timer = Class.forName("punchymoves.client.animation.LocomotionController$TimerControl");
                CLIP = field(layer, "clip", AnimationLibrary.Clip.class);
                TIME = field(layer, "time", float.class);
                FINISHED = method(layer, "finished", boolean.class);
                Class<?> controller = Class.forName("punchymoves.client.animation.LocomotionController");
                SWORD = field(controller, "swordAttack", layer).asType(MethodType.methodType(Object.class, Object.class));
                TOOL = field(controller, "toolSwing", layer).asType(MethodType.methodType(Object.class, Object.class));
                PUNCH = field(controller, "punch", layer).asType(MethodType.methodType(Object.class, Object.class));
                MODIFIER = method(timer, "modifier", float.class);
                CONDITION = method(timer, "condition", boolean.class);
                var constructor = timer.getDeclaredConstructor(float.class, boolean.class); constructor.setAccessible(true);
                TIMER = MethodHandles.lookup().unreflectConstructor(constructor)
                        .asType(MethodType.methodType(Object.class, float.class, boolean.class));
            } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
        private static MethodHandle field(Class<?> owner, String name, Class<?> result) throws ReflectiveOperationException {
            var field = owner.getDeclaredField(name); field.setAccessible(true);
            return MethodHandles.lookup().unreflectGetter(field).asType(MethodType.methodType(result, Object.class));
        }
        private static MethodHandle method(Class<?> owner, String name, Class<?> result) throws ReflectiveOperationException {
            var method = owner.getDeclaredMethod(name); method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(result, Object.class));
        }
    }
    public static boolean knownRecovery(Object controller, boolean tool, boolean sword) {
        try {
            Object timeline = (Object) (tool ? Access.TOOL : sword ? Access.SWORD : Access.PUNCH).invokeExact(controller);
            AnimationLibrary.Clip clip = (AnimationLibrary.Clip) Access.CLIP.invokeExact(timeline);
            return clip != null && AttackPacing.knownThirdPersonClip(clip.name(), clip.length());
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Requalify Moves 1.0.9's recovery layer", failure); }
    }
    public static Object timer(Object original, Object timeline, String type, AttackSnapshot attack, boolean enabled) {
        if (!enabled || attack == null || attack.session() != ClientAttackBridge.session() || !type.equals("attack")
                || timeline == null || !timeline.getClass().getName().equals(LAYER)) return original;
        try {
            AnimationLibrary.Clip clip = (AnimationLibrary.Clip) Access.CLIP.invokeExact(timeline);
            if (clip == null || (boolean) Access.FINISHED.invokeExact(timeline)) return original;
            float factor = AttackPacing.thirdPerson(attack, clip.name(), clip.length(), (float) Access.TIME.invokeExact(timeline));
            if (factor == 1) return original;
            float modifier = (float) Access.MODIFIER.invokeExact(original);
            boolean condition = (boolean) Access.CONDITION.invokeExact(original);
            float adjusted = modifier * factor;
            if (!condition || !Float.isFinite(adjusted) || adjusted <= 0) return original;
            return (Object) Access.TIMER.invokeExact(adjusted, condition);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Requalify Moves 1.0.9's attack clock", failure); }
    }
    private MovesPacing() {}
}
