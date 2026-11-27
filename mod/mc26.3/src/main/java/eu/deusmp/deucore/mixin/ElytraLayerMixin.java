package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.deusmp.deucore.cosmetics.DeuAvatarState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blendet Minecrafts Elytra aus, wenn der Spieler stattdessen seine DeuClient-Fluegel zeigen will. */
@Mixin(WingsLayer.class)
public abstract class ElytraLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void deucore$hide(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot,
                              CallbackInfo ci) {
        if (state instanceof DeuAvatarState deu && deu.deucore$getElytraPose() >= 0) ci.cancel();
    }
}
