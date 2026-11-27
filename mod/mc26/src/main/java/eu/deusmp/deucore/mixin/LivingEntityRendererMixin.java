package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Eigener Name über dem Kopf: In der Ansicht von hinten/vorne (F5) zeigt Minecraft
 * normalerweise kein eigenes Namensschild. Der DeuClient zeigt es so, wie andere
 * Spieler es sehen (inkl. Rang-Präfix vom Server und Deu-Logo).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void deucore$ownName(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !DeuConfig.get().ownNametag) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || mc.options.getCameraType().isFirstPerson()) return;
        if (entity.isInvisible() || mc.gui.screen() != null) return;
        cir.setReturnValue(true);
    }
}
