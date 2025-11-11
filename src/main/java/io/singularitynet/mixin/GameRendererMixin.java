package io.singularitynet.mixin;

import io.singularitynet.utils.TextureHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow private MinecraftClient client;

    @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void vereya$dualPass(WorldRenderer instance, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        if (!TextureHelper.isProducingColourMap()) {
            instance.render(tickCounter, renderBlockOutline, camera, gameRenderer, lightmapTextureManager, modelViewMatrix, projectionMatrix);
            return;
        }

        // Ensure segmentation FBO matches the current main framebuffer size.
        Framebuffer main = this.client.getFramebuffer();
        TextureHelper.ensureSegmentationFramebuffer(main.textureWidth, main.textureHeight);

        // First pass: segmentation colour map into off-screen FBO.
        TextureHelper.colourmapFrame = true;
        TextureHelper.beginSegmentationPass();
        instance.render(tickCounter, renderBlockOutline, camera, gameRenderer, lightmapTextureManager, modelViewMatrix, projectionMatrix);
        TextureHelper.endSegmentationPass();
        TextureHelper.colourmapFrame = false;

        // Second pass: normal rendering to main framebuffer.
        instance.render(tickCounter, renderBlockOutline, camera, gameRenderer, lightmapTextureManager, modelViewMatrix, projectionMatrix);
    }
}

