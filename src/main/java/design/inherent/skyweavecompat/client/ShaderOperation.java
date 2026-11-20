package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.neoforge.client.ClientWorkflowLayout;
import design.inherent.reform.neoforge.client.OperationScreen;
import design.inherent.reform.operation.OperationDescriptor;
import design.inherent.reform.operation.OperationScope;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.value.ValueTypes;
import design.inherent.skyweavecompat.shader.ShaderWorkspace;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Admitted native shader reload participates in the shared identified operation service. */
final class ShaderOperation {
    private final OwnerContext context;
    private final OperationScope operations;
    private final OperationDescriptor<Boolean, List<ShaderWorkspace.Status>> rebuild = new OperationDescriptor<>(
            new DocumentId("skyweave_compat", "shaders/rebuild"), ValueTypes.bool(), OwnerContext.Right.WRITE,
            Set.of(OperationDescriptor.Effect.PERSIST, OperationDescriptor.Effect.APPLY), Set.of(OperationDescriptor.EntryPoint.GUI));
    private final OperationDescriptor<Boolean, List<ShaderWorkspace.Status>> status = new OperationDescriptor<>(
            new DocumentId("skyweave_compat", "shaders/status"), ValueTypes.bool(), OwnerContext.Right.READ,
            Set.of(), Set.of(OperationDescriptor.EntryPoint.GUI));

    ShaderOperation() {
        var client = Minecraft.getInstance();
        context = new OwnerContext(client::execute, Runnable::run, () -> {
            if (!client.isSameThread()) throw new IllegalStateException("Shader assembly requires the render thread");
        }, OwnerContext.Limits.STANDARD);
        var access = context.access(() -> true, () -> true);
        operations = new OperationScope(context, access, new OperationScope.Limits(2, 1, 8, OperationScope.Limits.STANDARD.input()));
        operations.register(rebuild, (execution, ignored) -> context.onOwner(() -> {
            execution.checkpoint();
            ShaderAssembly.requestRebuild();
            try { net.irisshaders.iris.Iris.reload(); }
            catch (Exception failed) { throw new IllegalStateException("Shader reload failed", failed); }
            var states = List.copyOf(ShaderAssembly.states());
            var failed = states.stream().filter(state -> state.state().equals("failed")).findFirst();
            if (failed.isPresent()) throw new IllegalStateException(failed.get().id() + ": " + failed.get().detail());
            return states;
        }));
        operations.register(status, (execution, ignored) -> context.onOwner(() -> {
            execution.checkpoint();
            return List.copyOf(ShaderAssembly.states());
        }));
        operations.freeze();
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> context.close());
    }

    ClientWorkflowLayout.Action<List<ShaderWorkspace.Status>> action() {
        return new ClientWorkflowLayout.Action<>(rebuild.id(), text("shaders.rebuild"), text("shaders.rebuild_help"),
                () -> operations.hasActiveOperations() ? Optional.of(Component.literal("Shader assembly is still running")) : Optional.empty(),
                () -> operations.submit(rebuild, OperationDescriptor.EntryPoint.GUI, true), context::isOpen,
                ShaderOperation::outcome);
    }

    ClientWorkflowLayout.Action<List<ShaderWorkspace.Status>> statusAction() {
        return new ClientWorkflowLayout.Action<>(status.id(), text("shaders.status"), text("shaders.status_help"),
                () -> operations.hasActiveOperations() ? Optional.of(Component.literal("Shader assembly is still running")) : Optional.empty(),
                () -> operations.submit(status, OperationDescriptor.EntryPoint.GUI, true), context::isOpen, ShaderOperation::outcome);
    }

    private static OperationScreen.Outcome outcome(List<ShaderWorkspace.Status> states) {
        return new OperationScreen.Outcome(states.stream().map(state -> new OperationScreen.Detail(
                Component.literal(clip(state.id(), 128)),
                Component.literal(clip(state.state() + " " + state.output() + " " + state.detail(), 4000)))).toList());
    }

    private static String clip(String value, int limit) {
        return value.codePointCount(0, value.length()) <= limit ? value
                : value.substring(0, value.offsetByCodePoints(0, limit)) + "…";
    }

    private static Component text(String key) { return Component.translatable("skyweave_compat.settings." + key); }
}
