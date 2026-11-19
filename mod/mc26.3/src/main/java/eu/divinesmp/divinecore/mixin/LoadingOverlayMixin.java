package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Divine-Ladebildschirm: zeichnet am Ende von LoadingOverlay mit derselben Überblendung
 * über das Mojang-Logo – die Lade-Logik von Minecraft bleibt unverändert.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;
    @Shadow @Final private boolean fadeIn;
    @Shadow protected abstract void extractProgressBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float progress);

    /*
     * Mojang-Hintergrund, -Logo und -Balken werden nicht mehr gezeichnet (sonst scheinen sie waehrend des Ausblendens
     * durch den DeuClient-Ladebildschirm). Die Lade-Logik von Minecraft bleibt unveraendert.
     */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void divinecore$noBackground(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (!DivineConfig.get().customLoadingScreen) g.fill(x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void divinecore$noLogo(GuiGraphicsExtractor g, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, net.minecraft.resources.Identifier tex,
                                   int x, int y, float u, float v, int w, int h, int rw, int rh, int tw, int th, int color) {
        if (!DivineConfig.get().customLoadingScreen) g.blit(pipeline, tex, x, y, u, v, w, h, rw, rh, tw, th, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private void divinecore$noBar(LoadingOverlay self, GuiGraphicsExtractor g, int a, int b, int c, int d, float f) {
        if (!DivineConfig.get().customLoadingScreen) this.extractProgressBar(g, a, b, c, d, f);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void divinecore$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DivineConfig.get().customLoadingScreen) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();
        float fadeOut = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / (float) LoadingOverlay.FADE_OUT_TIME : -1f;
        float fadeInP = this.fadeInStart > -1L ? (now - this.fadeInStart) / (float) LoadingOverlay.FADE_IN_TIME : -1f;
        float opacity;
        if (fadeOut >= 1f) opacity = 1f - Mth.clamp(fadeOut - 1f, 0f, 1f);
        else if (this.fadeIn) opacity = Mth.clamp(fadeInP, 0f, 1f);
        else opacity = 1f;
        int a = (int) (opacity * 255);
        if (a <= 2) return;

        g.fillGradient(0, 0, w, h / 2, DivineRender.argb(0x0B1F12, a), DivineRender.argb(0x0F2A18, a));
        g.fillGradient(0, h / 2, w, h, DivineRender.argb(0x0F2A18, a), DivineRender.argb(DivineRender.NIGHT, a));

        float pulse = DivineRender.pulse(2600);
        int lw = Math.min(520, (int) (w * 0.7f));
        int lh = Math.round(lw * DivineRender.WORDMARK_H / (float) DivineRender.WORDMARK_W);
        int top = h / 2 - (lh + 36) / 2 - 10;

        // weicher gruener Schein hinter dem Logo
        for (int i = 4; i >= 1; i--) {
            int grow = i * 8;
            DivineRender.roundRect(g, w / 2 - lw / 2 - grow, top - grow, lw + grow * 2, lh + grow * 2, lh / 2 + grow,
                    DivineRender.argb(DivineRender.GOLD, (int) (a * 0.04f * (5 - i) * (0.6f + 0.4f * pulse))));
        }
        DivineRender.drawWordmark(g, w / 2 - lw / 2, top, lw, a);

        // Fortschrittsbalken (blendet beim Ausblenden zuerst aus – wie im Original)
        int ba = (int) (a * (1f - Mth.clamp(fadeOut, 0f, 1f)));
        if (ba > 2) {
            int bw = Math.min(260, (int) (w * 0.6f));
            int by = top + lh + 24;
            DivineRender.progressBar(g, w / 2 - bw / 2, by, bw, 10, this.currentProgress, ba);
            String pct = Math.round(this.currentProgress * 100) + "%";
            var font = Minecraft.getInstance().font;
            g.text(font, pct, w / 2 - font.width(pct) / 2, by + 15, DivineRender.argb(DivineRender.GOLD_LIGHT, ba), true);
        }
    }
}
