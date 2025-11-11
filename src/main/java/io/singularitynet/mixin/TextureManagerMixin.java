package io.singularitynet.mixin;

import io.singularitynet.utils.TextureHelper;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public class TextureManagerMixin {

    // Called whenever a texture is bound for rendering.
    @Inject(method = "bindTextureInner", at = @At("TAIL"))
    private void vereya$afterBindTexture(Identifier id, CallbackInfo ci) {
        TextureHelper.onTextureBound(id);
    }
}

