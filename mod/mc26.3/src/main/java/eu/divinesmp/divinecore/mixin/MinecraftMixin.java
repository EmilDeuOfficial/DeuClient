package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineCore;
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
    private void divinecore$title(CallbackInfoReturnable<String> cir) {
        if (DivineConfig.get().customWindowTitle) {
            cir.setReturnValue("DeuClient - Minecraft " + SharedConstants.getCurrentVersion().name());
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void divinecore$icon(GameConfig config, CallbackInfo ci) {
        DivineCore.applyWindowIcon(((Minecraft) (Object) this).getWindow());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void divinecore$tick(CallbackInfo ci) {
        DivineCore.onClientTick((Minecraft) (Object) this);
    }
}
