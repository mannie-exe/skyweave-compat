package design.inherent.skyweavecompat.accessories;

import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories_compat.curios.wrapper.CuriosConversionUtils;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import top.theillusivec4.curios.common.data.CuriosEntityManager;

/** Restores the one post-tag resolver event suppressed by the compatibility layer. */
public interface CuriosEntityBindings {
    void skyweave$publishCuriosBindings();
    void skyweave$releaseCuriosBindings();

    static void register() {
        NeoForge.EVENT_BUS.addListener(CuriosEntityBindings::afterTags);
        NeoForge.EVENT_BUS.addListener(CuriosEntityBindings::serverStopped);
    }

    private static CuriosEntityBindings loader() {
        return (CuriosEntityBindings) (Object) EntitySlotLoader.INSTANCE;
    }

    private static void afterTags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        CuriosEntityManager.SERVER.rebuildEntitySlots(event.getRegistryAccess().registryOrThrow(Registries.ENTITY_TYPE));
        loader().skyweave$publishCuriosBindings();
    }

    private static void serverStopped(ServerStoppedEvent event) {
        loader().skyweave$releaseCuriosBindings();
        CuriosConversionUtils.CURRENT_ENTITY_BINDINGS.clear();
    }
}
