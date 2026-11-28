package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.deusmp.deucore.cosmetics.WingsLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.1: Elytra ausblenden, wenn die Fluegel sie ersetzen. */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void deucore$hide(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b, float c,
                              float d, float e, float f, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player && WingsLayer.elytraPose(player) >= 0) ci.cancel();
    }
}
