package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineRender;
import eu.divinesmp.divinecore.screen.DivineModMenuScreen;
import eu.divinesmp.divinecore.screen.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * - Divine-Hintergrund für alle Menüs außerhalb einer Welt (Hauptmenü, Multiplayer, Optionen, Verbinden …)
 * - Divine-Overlay, Schriftzug und "Divine-Menü"-Button im Pause-Menü
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
    private void divinecore$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.minecraft == null) return;
        DivineConfig cfg = DivineConfig.get();
        if (this.minecraft.level == null && cfg.customBackground) {
            if (cfg.panoramaBackground) {
                // clean: verschwommenes Welt-Panorama + dunkler, leicht violetter Schleier
                extractPanorama(g, delta);
                extractBlurredBackground(g);
                g.fillGradient(0, 0, this.width, this.height, 0xA00A0F0C, 0xC8050806);
                g.fillGradient(0, 0, this.width, this.height / 2, 0x220B1F12, 0x00000000);
            } else {
                DivineRender.menuBackground(g, this.width, this.height, 255);
            }
            this.minecraft.gui.hud.extractDeferredSubtitles();
            ci.cancel();
        } else if (((Object) this) instanceof PauseScreen && cfg.customPauseMenu) {
            DivineRender.pauseOverlay(g, this.width, this.height);
            int lw = Math.min(150, this.width / 3);
            DivineRender.drawWordmark(g, this.width / 2 - lw / 2, 2, lw, 230);
            this.minecraft.gui.hud.extractDeferredSubtitles();
            ci.cancel();
        }
    }

    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void divinecore$pauseButton(int w, int h, CallbackInfo ci) {
        if (((Object) this) instanceof PauseScreen) {
            Screen self = (Screen) (Object) this;
            // oben links – oben rechts erscheinen Toasts (Hinweise, Erfolge)
            addRenderableWidget(new Ui.IconButton(6, 6, 96, 20, Ui.I_GRID, Component.literal("MOD-MENÜ"),
                    b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(self))).selected(true).bold());
            addRenderableWidget(new Ui.IconButton(106, 6, 96, 20, Ui.I_WINGS, Component.literal("KOSMETIK"),
                    b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(self, DivineModMenuScreen.Tab.COSMETICS))));
        }
    }
}
