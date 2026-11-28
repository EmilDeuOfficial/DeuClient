package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
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
 * Deu-Ladebildschirm: zeichnet am Ende von LoadingOverlay mit derselben Überblendung
 * über das Mojang-Logo - die Lade-Logik von Minecraft bleibt unverändert.
 */
// Fassung bis 1.21.5
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
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void deucore$noBackground(GuiGraphicsExtractor g, net.minecraft.client.renderer.RenderType type, int x1, int y1, int x2, int y2, int color) {
        if (!DeuConfig.get().customLoadingScreen) g.fill(type, x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void deucore$noLogo(GuiGraphicsExtractor g, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, net.minecraft.resources.Identifier tex,
                                   int x, int y, float u, float v, int w, int h, int rw, int rh, int tw, int th, int color) {
        if (!DeuConfig.get().customLoadingScreen) g.blit(pipeline, tex, x, y, u, v, w, h, rw, rh, tw, th, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private void deucore$noBar(LoadingOverlay self, GuiGraphicsExtractor g, int a, int b, int c, int d, float f) {
        if (!DeuConfig.get().customLoadingScreen) this.extractProgressBar(g, a, b, c, d, f);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void deucore$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DeuConfig.get().customLoadingScreen) return;
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

        g.fillGradient(0, 0, w, h / 2, DeuRender.argb(0x12151A, a), DeuRender.argb(0x1B1E24, a));
        g.fillGradient(0, h / 2, w, h, DeuRender.argb(0x1B1E24, a), DeuRender.argb(DeuRender.NIGHT, a));

        float pulse = DeuRender.pulse(2600);
        int lw = Math.min(520, (int) (w * 0.7f));
        int lh = Math.round(lw * DeuRender.WORDMARK_H / (float) DeuRender.WORDMARK_W);
        int top = h / 2 - (lh + 36) / 2 - 10;

        DeuRender.drawWordmark(g, w / 2 - lw / 2, top, lw, a);

        // Fortschrittsbalken (blendet beim Ausblenden zuerst aus - wie im Original)
        int ba = (int) (a * (1f - Mth.clamp(fadeOut, 0f, 1f)));
        if (ba > 2) {
            int bw = Math.min(260, (int) (w * 0.6f));
            int by = top + lh + 24;
            DeuRender.progressBar(g, w / 2 - bw / 2, by, bw, 10, this.currentProgress, ba);
            DeuRender.percent(g, w / 2, by + 15, this.currentProgress, DeuRender.argb(DeuRender.GOLD_LIGHT, ba));
        }
    }
}
