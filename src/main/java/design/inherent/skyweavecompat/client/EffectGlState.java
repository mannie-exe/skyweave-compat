package design.inherent.skyweavecompat.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import design.inherent.skyweavecompat.mixin.client.OutlineShaderCacheAccess;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL14C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;

/** State touched by the native GlowHalo allocation/blur/composite. No texture or FBO ownership. */
final class EffectGlState implements AutoCloseable {
    private final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final int program = glGetInteger(GL_CURRENT_PROGRAM), active = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int depthFunction = glGetInteger(GL_DEPTH_FUNC);
    private final boolean depth = glIsEnabled(GL_DEPTH_TEST), depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
    private final int sourceRgb = glGetInteger(GL_BLEND_SRC_RGB), destinationRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int sourceAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), destinationAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int shaderTexture0 = RenderSystem.getShaderTexture(0), shaderTexture1 = RenderSystem.getShaderTexture(1);
    private final ShaderInstance shader = RenderSystem.getShader();
    private final ShaderInstance appliedShader = OutlineShaderCacheAccess.skyweave$lastShader();
    private final int appliedProgram = OutlineShaderCacheAccess.skyweave$lastProgram();
    private final int vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING), vertexBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int texture0, texture1, activeBinding, colorMask;
    private boolean closed;

    EffectGlState() {
        glGetIntegerv(GL_VIEWPORT, viewport);
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            colorMask = (mask.get(0) != 0 ? 1 : 0) | (mask.get(1) != 0 ? 2 : 0)
                    | (mask.get(2) != 0 ? 4 : 0) | (mask.get(3) != 0 ? 8 : 0);
        }
        activeBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL_TEXTURE0); texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL_TEXTURE1); texture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(active);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(depthMask);
        RenderSystem.colorMask((colorMask & 1) != 0, (colorMask & 2) != 0, (colorMask & 4) != 0, (colorMask & 8) != 0);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, shaderTexture0); RenderSystem.setShaderTexture(1, shaderTexture1);
        GlStateManager._activeTexture(GL_TEXTURE0); GlStateManager._bindTexture(texture0);
        GlStateManager._activeTexture(GL_TEXTURE1); GlStateManager._bindTexture(texture1);
        GlStateManager._activeTexture(active); GlStateManager._bindTexture(activeBinding);
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        OutlineShaderCacheAccess.skyweave$lastShader(appliedShader);
        OutlineShaderCacheAccess.skyweave$lastProgram(appliedProgram);
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(vertexArray);
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    }
}
