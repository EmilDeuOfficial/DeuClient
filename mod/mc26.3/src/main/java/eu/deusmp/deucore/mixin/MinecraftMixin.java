package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuCore;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fenstertitel, Fenster-Icon und Rechts-Shift-Abfrage (jeden Tick). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void deucore$title(CallbackInfoReturnable<String> cir) {
        if (DeuConfig.get().customWindowTitle) {
            cir.setReturnValue("DeuClient - Minecraft " + SharedConstants.getCurrentVersion().name());
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void deucore$icon(GameConfig config, CallbackInfo ci) {
        DeuCore.applyWindowIcon(((Minecraft) (Object) this).getWindow());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void deucore$tick(CallbackInfo ci) {
        DeuCore.onClientTick((Minecraft) (Object) this);
    }
}
