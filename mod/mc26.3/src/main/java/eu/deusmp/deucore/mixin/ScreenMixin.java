package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.DeuModMenuScreen;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * - Deu-Hintergrund für alle Menüs außerhalb einer Welt (Hauptmenü, Multiplayer, Optionen, Verbinden …)
 * - Deu-Overlay, Schriftzug und "Deu-Menü"-Button im Pause-Menü
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow @Final protected Minecraft minecraft;
    @Shadow public int width;
    @Shadow public int height;

    @Shadow
    protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Shadow
    protected abstract void extractPanorama(GuiGraphicsExtractor g, float delta);

    @Shadow
    protected abstract void extractBlurredBackground(GuiGraphicsExtractor g);

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void deucore$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.minecraft == null) return;
        DeuConfig cfg = DeuConfig.get();
        if (!cfg.customBackground) return;
        Object self = this;
        if (this.minecraft.level == null) {
            if (cfg.panoramaBackground) {
                // clean: verschwommenes Welt-Panorama + dunkler, gruener Schleier
                extractPanorama(g, delta);
                extractBlurredBackground(g);
                g.fillGradient(0, 0, this.width, this.height, 0xA0121418, 0xC807080A);
                g.fillGradient(0, 0, this.width, this.height / 2, 0x2212151A, 0x00000000);
            } else {
                DeuRender.menuBackground(g, this.width, this.height, 255);
            }
        } else if (self instanceof AbstractContainerScreen<?> || self instanceof ChatScreen) {
            return; // Inventare und Chat behalten ihren normalen Hintergrund
        } else {
            // in der Welt: Welt verschwommen + gruener Schleier (wie im Hauptmenue)
            extractBlurredBackground(g);
            g.fillGradient(0, 0, this.width, this.height, 0x8C0C0D10, 0xB407080A);
            g.fillGradient(0, 0, this.width, this.height / 2, 0x3012151A, 0x00000000);
        }
        this.minecraft.gui.hud.extractDeferredSubtitles();
        ci.cancel();
    }


    private boolean deucore$themed() {
        if (this.minecraft == null || !DeuConfig.get().customBackground) return false;
        Object self = this;
        return !(self instanceof AbstractContainerScreen<?>) && !(self instanceof ChatScreen);
    }

    private void deucore$tint(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0x8C0C0D10, 0xB407080A);
    }

    /** Menues in der Welt (ohne eigenen Hintergrund): Blur + gruener Schleier */
    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void deucore$transparent(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!deucore$themed()) return;
        extractBlurredBackground(g);
        deucore$tint(g, 0, 0, this.width, this.height);
        ci.cancel();
    }

    /** Vanilla-Menuehintergrund (dunkle Kacheln) durch gruenen Schleier ersetzen */
    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void deucore$menuBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!deucore$themed()) return;
        deucore$tint(g, 0, 0, this.width, this.height);
        ci.cancel();
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
    private void deucore$menuBackgroundArea(GuiGraphicsExtractor g, int x, int y, int w, int h, CallbackInfo ci) {
        if (!deucore$themed()) return;
        deucore$tint(g, x, y, w, h);
        ci.cancel();
    }

    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void deucore$pauseButton(int w, int h, CallbackInfo ci) {
        if (((Object) this) instanceof PauseScreen) {
            Screen self = (Screen) (Object) this;
            // oben links - oben rechts erscheinen Toasts (Hinweise, Erfolge)
            addRenderableWidget(new Ui.IconButton(6, 6, 96, 20, Ui.I_GRID, Component.literal("MODS"),
                    b -> this.minecraft.gui.setScreen(new DeuModMenuScreen(self))).selected(true).bold());
            addRenderableWidget(new Ui.IconButton(106, 6, 96, 20, Ui.I_WINGS, Component.literal("KOSMETIK"),
                    b -> this.minecraft.gui.setScreen(new DeuModMenuScreen(self, DeuModMenuScreen.Tab.COSMETICS))));
        }
    }
}
