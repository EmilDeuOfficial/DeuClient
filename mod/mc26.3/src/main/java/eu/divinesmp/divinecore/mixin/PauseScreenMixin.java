package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Divine-Overlay + Schriftzug im Pause-Menü (PauseScreen zeichnet seinen Hintergrund selbst). */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void divinecore$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DivineConfig.get().customPauseMenu) return;
        int w = g.guiWidth(), h = g.guiHeight();
        DivineRender.pauseOverlay(g, w, h);
        // Schriftzug nur, wenn er weder die Buttons oben links noch den Titel "Spielmenü" verdeckt
        int lw = Math.min(110, w / 4);
        if (w / 2 - lw / 2 > 214 && h >= 260) DivineRender.drawWordmark(g, w / 2 - lw / 2, 2, lw, 230);
        Minecraft.getInstance().gui.hud.extractDeferredSubtitles();
        ci.cancel();
    }
}
