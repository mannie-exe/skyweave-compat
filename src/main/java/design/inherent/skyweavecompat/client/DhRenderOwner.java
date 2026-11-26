/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** The same native render owner guards both level-close and executor-close waits. */
public record DhRenderOwner(long context) {
    public static DhRenderOwner capture() {
        return new DhRenderOwner(RenderSystem.isOnRenderThread() ? GLFW.glfwGetCurrentContext() : 0);
    }

    public boolean isCurrent() {
        if (context == 0 || !RenderSystem.isOnRenderThread() || GLFW.glfwGetCurrentContext() != context) return false;
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getWindow() != null && client.getWindow().getWindow() == context
                && RenderThreadTaskHandler.INSTANCE.isCurrentThread();
    }

    public void service() {
        if (isCurrent()) RenderThreadTaskHandler.INSTANCE.runRenderThreadTasks();
    }
}
