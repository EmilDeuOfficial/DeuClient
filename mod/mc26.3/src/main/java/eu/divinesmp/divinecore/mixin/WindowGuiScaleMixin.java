package eu.divinesmp.divinecore.mixin;

import com.mojang.blaze3d.platform.Window;
import eu.divinesmp.divinecore.DivineConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eigene GUI-Skalierung mit halben Stufen (z. B. 2,5). Minecraft kennt nur ganze Zahlen;
 * hier wird die skalierte Fenstergroesse mit dem gewuenschten Faktor selbst berechnet.
 */
@Mixin(Window.class)
public abstract class WindowGuiScaleMixin {
    @Shadow private int framebufferWidth;
    @Shadow private int framebufferHeight;
    @Shadow private int guiScaledWidth;
    @Shadow private int guiScaledHeight;
    @Shadow private int guiScale;

    @Inject(method = "setGuiScale", at = @At("HEAD"), cancellable = true)
    private void divinecore$guiScale(int scale, CallbackInfo ci) {
        double wanted = DivineConfig.guiScaleWanted();
        if (wanted <= 0) return;
        this.guiScale = Math.max(1, (int) Math.round(wanted));
        this.guiScaledWidth = (int) Math.ceil(this.framebufferWidth / wanted);
        this.guiScaledHeight = (int) Math.ceil(this.framebufferHeight / wanted);
        ci.cancel();
    }
}
