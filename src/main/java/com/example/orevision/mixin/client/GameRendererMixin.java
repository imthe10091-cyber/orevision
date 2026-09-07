package com.example.orevision.mixin.client;

import com.example.orevision.render.OreEspRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases our StagedVertexBuffer's GPU resources when the game renderer
 * shuts down, matching the cleanup pattern documented at
 * https://docs.fabricmc.net/develop/rendering/world for custom render
 * pipelines using StagedVertexBuffer.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "close", at = @At("RETURN"))
    private void onGameRendererClose(CallbackInfo ci) {
        OreEspRenderer.close();
    }
}
