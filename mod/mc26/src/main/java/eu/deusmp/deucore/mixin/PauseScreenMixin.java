package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DeuClient-Logo im Pause-Menue, direkt ueber dem obersten Button ("Back to Game").
 * Den gruenen Blur-Hintergrund zeichnen die Hooks in ScreenMixin (extractMenuBackground).
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void deucore$logo(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DeuConfig.get().customPauseMenu) return;
        Screen self = (Screen) (Object) this;
        int w = self.width, h = self.height;
        int topBtn = Integer.MAX_VALUE;
        for (GuiEventListener child : self.children()) {
            if (child instanceof AbstractButton b && b.getX() <= w / 2 && b.getX() + b.getWidth() >= w / 2) {
                topBtn = Math.min(topBtn, b.getY());
            }
        }
        if (topBtn == Integer.MAX_VALUE) topBtn = Math.round(h * 0.27f);
        int lw = Math.min(240, w / 3);
        int lh = Math.round(lw * DeuRender.WORDMARK_H / (float) DeuRender.WORDMARK_W);
        DeuRender.drawWordmark(g, w / 2 - lw / 2, Math.max(8, topBtn - lh - 14), lw, 235);
    }
}
