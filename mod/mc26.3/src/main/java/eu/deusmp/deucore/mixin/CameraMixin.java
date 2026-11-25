package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: verkleinert das Sichtfeld, solange die Zoom-Taste gehalten wird. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void deucore$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        float factor = Zoom.currentFactor(partialTick);
        if (factor > 1.001f) cir.setReturnValue(cir.getReturnValue() / factor);
    }
}
