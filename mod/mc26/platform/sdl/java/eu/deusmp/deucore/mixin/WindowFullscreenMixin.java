package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vollbild ist immer "Borderless" (randloses Fenster) statt exklusivem Vollbild. */
@Mixin(Window.class)
public abstract class WindowFullscreenMixin {
    @Shadow private boolean exclusiveFullscreen;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void deucore$borderless(CallbackInfo ci) {
        this.exclusiveFullscreen = false;
    }

    @ModifyVariable(method = "setExclusiveFullscreen", at = @At("HEAD"), argsOnly = true)
    private boolean deucore$noExclusive(boolean exclusive) {
        return false;
    }
}
