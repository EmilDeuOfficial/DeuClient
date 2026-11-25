package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt den Mojang-Ladebildschirm optisch durch den Deu-Ladebildschirm.
 *
 * Die Lade-Logik von Minecraft bleibt unverändert - wir zeichnen am Ende von render()
 * mit derselben Überblendung (Alpha) über den Original-Bildschirm:
 * Deu-Farben, Logo, Schriftzug und eigener Fortschrittsbalken.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;
    @Shadow @Final private boolean reloading;

    @Shadow protected abstract void renderProgressBar(DrawContext ctx, int x1, int y1, int x2, int y2, float opacity);

    /*
     * Mojang-Hintergrund, -Logo und -Balken werden nicht gezeichnet (sonst scheinen sie waehrend des Ausblendens
     * durch den DeuClient-Ladebildschirm). Die Lade-Logik von Minecraft bleibt unveraendert.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(Lnet/minecraft/client/render/RenderLayer;IIIII)V"))
    private void deucore$noBackground(DrawContext ctx, net.minecraft.client.render.RenderLayer layer, int x1, int y1, int x2, int y2, int color) {
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V"))
    private void deucore$noLogo(DrawContext ctx, java.util.function.Function<net.minecraft.util.Identifier, net.minecraft.client.render.RenderLayer> layers,
                                   net.minecraft.util.Identifier tex, int x, int y, float u, float v, int w, int h, int rw, int rh, int tw, int th, int color) {
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/SplashOverlay;renderProgressBar(Lnet/minecraft/client/gui/DrawContext;IIIIF)V"))
    private void deucore$noBar(SplashOverlay self, DrawContext ctx, int x1, int y1, int x2, int y2, float opacity) {
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void deucore$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // gleiche Ein-/Ausblendung wie im Original berechnen
        long now = Util.getMeasuringTimeMs();
        float fadeOut = this.reloadCompleteTime > -1L ? (now - this.reloadCompleteTime) / 1000f : -1f;
        float fadeIn = this.reloadStartTime > -1L ? (now - this.reloadStartTime) / 500f : -1f;
        float opacity;
        if (fadeOut >= 1f) opacity = 1f - MathHelper.clamp(fadeOut - 1f, 0f, 1f);
        else if (this.reloading) opacity = MathHelper.clamp(fadeIn, 0f, 1f);
        else opacity = 1f;
        int a = (int) (opacity * 255);
        if (a <= 2) return;

        // Hintergrund
        ctx.fillGradient(0, 0, w, h / 2, DeuRender.argb(0x0B2014, a), DeuRender.argb(0x0F2B1A, a));
        ctx.fillGradient(0, h / 2, w, h, DeuRender.argb(0x0F2B1A, a), DeuRender.argb(0x050706, a));

        // Logo (mit pulsierendem gruenem Schein)
        float pulse = (MathHelper.sin(now / 420f) + 1f) / 2f;
        int lw = Math.min(520, (int) (w * 0.7f));
        int lh = Math.round(lw * DeuRender.WORDMARK_H / (float) DeuRender.WORDMARK_W);
        int top = h / 2 - (lh + 36) / 2 - 10;

        for (int i = 4; i >= 1; i--) {
            int grow = i * 8;
            DeuRender.roundedRect(ctx, w / 2 - lw / 2 - grow, top - grow, lw + grow * 2, lh + grow * 2, DeuRender.argb(DeuRender.GOLD, (int) (a * 0.04f * (5 - i) * (0.6f + 0.4f * pulse))));
        }
        DeuRender.drawWordmark(ctx, w / 2 - lw / 2, top, lw, a);

        // Fortschrittsbalken (wird wie im Original beim Ausblenden zuerst unsichtbar)
        float barAlpha = 1f - MathHelper.clamp(fadeOut, 0f, 1f);
        int ba = (int) (a * barAlpha);
        int bw = Math.min(260, (int) (w * 0.6f));
        int by = top + lh + 24;
        if (ba > 2) {
            DeuRender.progressBar(ctx, w / 2 - bw / 2, by, bw, 10, this.progress, ba);
            String pct = Math.round(this.progress * 100) + "%";
            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            ctx.drawTextWithShadow(tr, pct, w / 2 - tr.getWidth(pct) / 2, by + 15, DeuRender.argb(DeuRender.GOLD_LIGHT, ba));
        }
    }
}
