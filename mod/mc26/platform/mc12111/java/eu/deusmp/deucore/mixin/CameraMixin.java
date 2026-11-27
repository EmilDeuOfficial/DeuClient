package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: verkleinert das Sichtfeld, solange die Zoom-Taste gehalten wird (bis 1.21.11 im GameRenderer). */
@Mixin(GameRenderer.class)
public abstract class CameraMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void deucore$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (!useFovSetting) return; // die Hand behaelt ihr Sichtfeld
        float factor = Zoom.currentFactor(partialTick);
        if (factor > 1.001f) cir.setReturnValue(cir.getReturnValue() / factor);
    }
}
