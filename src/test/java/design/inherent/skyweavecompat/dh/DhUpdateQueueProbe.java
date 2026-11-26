package design.inherent.skyweavecompat.dh;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import design.inherent.skyweavecompat.rendering.TestMixinService;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Actual DH manager/queue/worker, with only its platform wrapper and chunk input controlled. */
public final class DhUpdateQueueProbe {
    public static void main(String[] args) throws Exception {
        var loader = DhUpdateQueueProbe.class.getClassLoader();
        var wrapper = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper");
        var bindable = Class.forName("com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable");
        Object platform = Proxy.newProxyInstance(loader, new Class<?>[]{wrapper}, (proxy, method, arguments) -> switch (method.getName()) {
            case "getDelayedSetupComplete", "isServerThreadHealthy", "isDedicatedServer" -> true;
            case "getPlayerCount" -> 0;
            case "getInstallationDirectory" -> new java.io.File(".");
            default -> method.getReturnType() == boolean.class ? false : null;
        });
        if (java.util.List.of(args).contains("client")) {
            var clientType = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper");
            var posType = Class.forName("com.seibel.distanthorizons.core.pos.DhChunkPos");
            Object center = posType.getConstructor(int.class, int.class).newInstance(2, 3);
            Object client = Proxy.newProxyInstance(loader, new Class<?>[]{clientType}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getDelayedSetupComplete", "playerExists", "clientConnectedToDedicatedServer" -> true;
                case "getPlayerChunkPos" -> center;
                default -> method.getReturnType() == boolean.class ? false : null;
            });
            var type = Class.forName("com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector");
            type.getMethod("bind", Class.class, bindable).invoke(type.getField("INSTANCE").get(null), clientType, client);
        }
        var injectorType = Class.forName("com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector");
        var injector = injectorType.getField("INSTANCE").get(null);
        injectorType.getMethod("bind", Class.class, bindable).invoke(injector, wrapper, platform);
        // Native startup initializes the preset owner before the worker reads its dependent entries.
        Class.forName("com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler");
        String target = "com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateQueueManager";
        boolean patched = java.util.List.of(args).contains("patched");
        ClassLoader managerLoader = loader;
        if (patched) {
            MixinBootstrap.init();
            var service = (TestMixinService) MixinService.getService();
            var transformer = service.transformer();
            MixinEnvironment.getCurrentEnvironment().setActiveTransformer(transformer);
            MixinExtrasBootstrap.init();
            MixinEnvironment.getDefaultEnvironment().setSide(java.util.List.of(args).contains("client") ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
            Mixins.addConfiguration("dh-updatequeue-test.mixins.json");
            byte[] bytes;
            try (var input = service.getResourceAsStream(target.replace('.', '/') + ".class")) {
                bytes = transformer.transformClassBytes(target, target, input.readAllBytes());
            }
            managerLoader = new ClassLoader(loader) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!name.equals(target)) return super.loadClass(name, resolve);
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) loaded = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                }
            };
        }
        var managerType = Class.forName(target, true, managerLoader);
        var before = Thread.getAllStackTraces().keySet();
        Object manager = managerType.getConstructor(String.class).newInstance("compat-update-probe");
        var executorField = managerType.getDeclaredField("queueingThread"); executorField.setAccessible(true);
        var executor = (ExecutorService) executorField.get(manager);
        try {
            var worker = Thread.getAllStackTraces().keySet().stream().filter(t -> !before.contains(t) && t.getName().contains("compat-update-probe")).findFirst().orElseThrow();
            var positionType = Class.forName("com.seibel.distanthorizons.core.pos.DhChunkPos");
            Object position = positionType.getConstructor(int.class, int.class).newInstance(2, 3);
            var chunkType = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper");
            Object chunk = Proxy.newProxyInstance(loader, new Class<?>[]{chunkType}, (proxy, method, arguments) -> {
                if (method.getName().equals("getChunkPos")) return position;
                if (method.getName().equals("copy")) return proxy;
                if (method.getName().equals("toString")) return "controlled chunk 2,3";
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
            var dataType = Class.forName("com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateData");
            var levelType = Class.forName("com.seibel.distanthorizons.core.level.IDhLevel");
            Object data = dataType.getConstructor(chunkType, levelType).newInstance(chunk, null);
            long firstQueued = dataType.getField("firstQueuedNanoTime").getLong(data);
            managerType.getMethod("addItemToUpdateQueue", positionType, dataType).invoke(manager, position, data);
            var bean = ManagementFactory.getThreadMXBean();
            if (bean.isThreadCpuTimeSupported() && !bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
            // The native empty-loop sleep is 50ms. Observe after that original sleep, but before debounce expires.
            TimeUnit.MILLISECONDS.sleep(java.util.List.of(args).contains("overdue") ? 310 : 65);
            long cpuStart = bean.isThreadCpuTimeSupported() ? bean.getThreadCpuTime(worker.threadId()) : -1;
            int runnable = 0, waiting = 0;
            for (int i = 0; i < 60; i++) {
                var state = worker.getState();
                if (state == Thread.State.RUNNABLE) runnable++;
                if (state == Thread.State.TIMED_WAITING || state == Thread.State.WAITING) waiting++;
                TimeUnit.MILLISECONDS.sleep(1);
            }
            long cpuEnd = bean.isThreadCpuTimeSupported() ? bean.getThreadCpuTime(worker.threadId()) : -1;
            if (!worker.isAlive()) throw new AssertionError("native worker terminated during observation");
            int queued = (int) managerType.getMethod("getQueuedCount").invoke(manager);
            if (queued != 1 || firstQueued != dataType.getField("firstQueuedNanoTime").getLong(data)) throw new AssertionError("native queue/debounce identity changed");
            System.out.println("DH_RETRY_OBSERVATION runnable=" + runnable + " waiting=" + waiting + " queued=" + queued + " workerCpuNanos=" + (cpuStart < 0 ? -1 : cpuEnd - cpuStart));
            if (java.util.List.of(args).contains("expect-wait") && waiting < 50) throw new AssertionError("nonempty immature queue must wait rather than busy-retry");
        } finally {
            managerType.getMethod("close").invoke(manager);
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) throw new AssertionError("native worker failed to close");
        }
    }
}
