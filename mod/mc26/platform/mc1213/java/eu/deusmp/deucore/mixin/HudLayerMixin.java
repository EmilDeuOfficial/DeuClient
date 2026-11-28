package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.compat.hud.HudElementRegistry;
import eu.deusmp.deucore.compat.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.3: Fadenkreuz und Wirkungs-Symbole koennen ersetzt werden. */
@Mixin(Gui.class)
public class HudLayerMixin {
    @Shadow private void renderCrosshair(GuiGraphics g, DeltaTracker delta) {}

    @Shadow private void renderEffects(GuiGraphics g, DeltaTracker delta) {}

    /** true, waehrend die Ersetzung das Original aufruft */
    @Unique private boolean deucore$original;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void deucore$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (deucore$original) return;
        if (HudElementRegistry.render(VanillaHudElements.CROSSHAIR, g, delta, (g2, d2) -> {
            deucore$original = true;
            try {
                renderCrosshair(g2, d2);
            } finally {
                deucore$original = false;
            }
        })) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void deucore$effects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (deucore$original) return;
        if (HudElementRegistry.render(VanillaHudElements.STATUS_EFFECTS, g, delta, (g2, d2) -> {
            deucore$original = true;
            try {
                renderEffects(g2, d2);
            } finally {
                deucore$original = false;
            }
        })) ci.cancel();
    }
}
