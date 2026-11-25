package eu.deusmp.deucore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;

/**
 * Zeichen-Helfer im Deu-Stil: Farben, abgerundete Flächen, animierter Menü-Hintergrund
 * sowie Logo und Schriftzug.
 */
public final class DeuRender {
    private DeuRender() {}

    /* ---------------- Farben (RGB, Alpha wird separat gesetzt) ---------------- */
    public static final int GOLD = 0x2EFF7A;
    public static final int GOLD_LIGHT = 0x9FFFC0;
    public static final int GOLD_DARK = 0x0F7A35;
    public static final int VIOLET = 0x1FD35F;
    public static final int VIOLET_MID = 0x0F7A35;
    public static final int VIOLET_DARK = 0x0A2014;
    public static final int NIGHT = 0x050706;

    public static final Identifier EMBLEM = Identifier.of(DeuCore.MOD_ID, "dynamic/emblem");
    public static final Identifier WORDMARK = Identifier.of(DeuCore.MOD_ID, "dynamic/wordmark");
    public static final int EMBLEM_SIZE = 256;
    public static final int WORDMARK_W = 1024;
    public static final int WORDMARK_H = 187;

    private static boolean texturesLoaded = false;

    public static int argb(int rgb, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Mischt zwei RGB-Farben (t = 0..1) */
    public static int lerpRgb(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Lädt Logo und Schriftzug direkt aus dem Mod-JAR als eigene Texturen.
     * So sind sie auch während des allerersten Ladebildschirms verfügbar,
     * bevor die Ressourcenpakete fertig geladen sind.
     */
    public static void ensureTextures() {
        if (texturesLoaded) return;
        texturesLoaded = true;
        register(EMBLEM, "/assets/deucore/textures/gui/emblem.png");
        register(WORDMARK, "/assets/deucore/textures/gui/wordmark.png");
    }

    private static void register(Identifier id, String path) {
        try (InputStream in = DeuRender.class.getResourceAsStream(path)) {
            if (in == null) return;
            NativeImageBackedTexture tex = new NativeImageBackedTexture(NativeImage.read(in));
            tex.setFilter(true, false); // weich skalieren
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        } catch (Exception e) {
            DeuCore.LOGGER.warn("Textur {} konnte nicht geladen werden", path, e);
        }
    }

    /* ---------------- Formen ---------------- */

    /*
     * Kantengeglaettete Rundungen: Nur die Ecken werden als weich gefilterte Textur gezeichnet
     * (vorberechnete Kreise in mehreren Groessen), alles Gerade per fill(). Keine Pixeltreppen.
     */
    private static final int[] CORNER_SIZES = {4, 8, 16, 32, 64, 128};
    private static final Identifier[] CORNER_IDS = new Identifier[CORNER_SIZES.length];

    private static Identifier cornerTexture(int idx) {
        if (CORNER_IDS[idx] == null) {
            int rad = CORNER_SIZES[idx];
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 2 * rad, 2 * rad, false);
            for (int py = 0; py < 2 * rad; py++) {
                for (int px = 0; px < 2 * rad; px++) {
                    int hit = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double dx = px + (sx + 0.5) / 4.0 - rad, dy = py + (sy + 0.5) / 4.0 - rad;
                            if (dx * dx + dy * dy <= (double) rad * rad) hit++;
                        }
                    }
                    img.setColorArgb(px, py, ((hit * 255 / 16) << 24) | 0xFFFFFF);
                }
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            tex.setFilter(true, false);
            Identifier id = Identifier.of(DeuCore.MOD_ID, "dynamic/corner/" + rad);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            CORNER_IDS[idx] = id;
        }
        return CORNER_IDS[idx];
    }

    private static void corners(DrawContext ctx, int x, int y, int w, int h, int r, int topColor, int bottomColor) {
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int idx = CORNER_SIZES.length - 1;
        for (int i = 0; i < CORNER_SIZES.length; i++) {
            if (CORNER_SIZES[i] >= r * scale) { idx = i; break; }
        }
        Identifier tex = cornerTexture(idx);
        int c = CORNER_SIZES[idx], t = 2 * c;
        ctx.drawTexture(RenderLayer::getGuiTextured, tex, x, y, 0f, 0f, r, r, c, c, t, t, topColor);
        ctx.drawTexture(RenderLayer::getGuiTextured, tex, x + w - r, y, (float) c, 0f, r, r, c, c, t, t, topColor);
        ctx.drawTexture(RenderLayer::getGuiTextured, tex, x, y + h - r, 0f, (float) c, r, r, c, c, t, t, bottomColor);
        ctx.drawTexture(RenderLayer::getGuiTextured, tex, x + w - r, y + h - r, (float) c, (float) c, r, r, c, c, t, t, bottomColor);
    }

    public static int lerpArgb(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | lerpRgb(a, b, t);
    }

    /** Rechteck mit weich abgerundeten Ecken */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        roundedRect(ctx, x, y, w, h, 3, color);
    }

    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + r, y, x + w - r, y + r, color);
        ctx.fill(x, y + r, x + w, y + h - r, color);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, color);
        corners(ctx, x, y, w, h, r, color, color);
    }

    /** Abgerundete Flaeche mit senkrechtem Farbverlauf */
    public static void roundedGradient(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        roundedGradient(ctx, x, y, w, h, 3, top, bottom);
    }

    public static void roundedGradient(DrawContext ctx, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        int t0 = lerpArgb(top, bottom, r / 2f / h), t1 = lerpArgb(top, bottom, r / (float) h);
        int b0 = lerpArgb(top, bottom, (h - r) / (float) h), b1 = lerpArgb(top, bottom, (h - r / 2f) / h);
        ctx.fillGradient(x + r, y, x + w - r, y + r, top, t1);
        ctx.fillGradient(x, y + r, x + w, y + h - r, t1, b0);
        ctx.fillGradient(x + r, y + h - r, x + w - r, y + h, b0, bottom);
        corners(ctx, x, y, w, h, r, t0, b1);
    }

    /** Abgerundete Karte mit goldenem Rand */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int alpha) {
        roundedRect(ctx, x, y, w, h, argb(GOLD, alpha / 3));
        roundedGradient(ctx, x + 1, y + 1, w - 2, h - 2, argb(0x10261A, alpha), argb(0x0A1409, alpha));
    }

    /* ---------------- Logo ---------------- */

    public static void drawEmblem(DrawContext ctx, int x, int y, int size, int alpha) {
        ensureTextures();
        drawScaled(ctx, EMBLEM, x, y, size / (float) EMBLEM_SIZE, EMBLEM_SIZE, EMBLEM_SIZE, alpha);
    }

    /** Zeichnet den "DEUCLIENT"-Schriftzug mit gegebener Breite; liefert die Höhe zurück */
    public static int drawWordmark(DrawContext ctx, int x, int y, int width, int alpha) {
        ensureTextures();
        float s = width / (float) WORDMARK_W;
        drawScaled(ctx, WORDMARK, x, y, s, WORDMARK_W, WORDMARK_H, alpha);
        return Math.round(WORDMARK_H * s);
    }

    private static void drawScaled(DrawContext ctx, Identifier tex, int x, int y, float scale, int tw, int th, int alpha) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        ctx.drawTexture(RenderLayer::getGuiTextured, tex, 0, 0, 0f, 0f, tw, th, tw, th, argb(0xFFFFFF, alpha));
        m.pop();
    }

    /* ---------------- Hintergründe ---------------- */

    /**
     * Animierter Deu-Hintergrund für alle Menüs außerhalb einer Welt:
     * langsam pulsierender Violett-Verlauf, Lichtschein und aufsteigende Goldpartikel.
     */
    public static void menuBackground(DrawContext ctx, int w, int h, int alpha) {
        long t = Util.getMeasuringTimeMs();
        float pulse = (MathHelper.sin(t / 2600f) + 1f) / 2f;

        int top = lerpRgb(0x07130A, 0x0B2014, pulse);
        int mid = lerpRgb(0x0F7A35, 0x0F7A35, 1f - pulse);
        int bottom = 0x050706;
        ctx.fillGradient(0, 0, w, h / 2, argb(top, alpha), argb(mid, alpha));
        ctx.fillGradient(0, h / 2, w, h, argb(mid, alpha), argb(bottom, alpha));

        // goldener Lichtstreifen am Horizont
        int glowA = (int) (alpha * (0.18f + 0.10f * pulse));
        ctx.fillGradient(0, h / 2, w, h / 2 + h / 6, argb(GOLD, 0), argb(GOLD, glowA / 2));
        ctx.fillGradient(0, h / 2 + h / 6, w, h / 2 + h / 3, argb(GOLD, glowA / 2), argb(GOLD, 0));

        // blockige Hügel-Silhouette (Minecraft-Stil)
        int ground = h - h / 5;
        for (int x = 0, i = 0; x < w; x += 16, i++) {
            int bump = (int) ((MathHelper.sin(i * 0.55f) + MathHelper.cos(i * 0.23f + 1f)) * 10f);
            ctx.fill(x, ground + bump, x + 16, h, argb(0x0A2014, Math.min(alpha, 220)));
        }

        // aufsteigende Goldpartikel (deterministisch aus der Zeit berechnet, keine Zustände nötig)
        for (int i = 0; i < 40; i++) {
            float speed = 0.012f + (i % 7) * 0.004f;
            float px = ((i * 97.13f) % 1f + (i * 0.6180339f) % 1f) % 1f * w;
            float py = h - ((t * speed + i * 211f) % (h + 40f)) + 20f;
            int size = 1 + (i % 3);
            float life = 1f - (h - py) / (float) h;
            int a = (int) (alpha * 0.9f * MathHelper.clamp(life, 0f, 1f));
            ctx.fill((int) px, (int) py, (int) px + size, (int) py + size, argb(i % 4 == 0 ? GOLD_LIGHT : GOLD, a));
        }

        // Vignette unten
        ctx.fillGradient(0, h - h / 3, w, h, argb(0x000000, 0), argb(0x000000, alpha / 2));
    }

    /** Dezenter Deu-Overlay für das Pause-Menü (Spielwelt bleibt sichtbar) */
    public static void pauseOverlay(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, argb(0x0A2014, 190), argb(0x050706, 225));
        ctx.fill(0, 0, w, 1, argb(GOLD, 120));
        ctx.fill(0, h - 1, w, h, argb(GOLD, 120));
    }

    /** Fortschrittsbalken im Deu-Stil */
    public static void progressBar(DrawContext ctx, int x, int y, int w, int h, float progress, int alpha) {
        roundedRect(ctx, x, y, w, h, argb(GOLD, alpha / 2));
        roundedRect(ctx, x + 1, y + 1, w - 2, h - 2, argb(0x0A1409, alpha));
        int fill = Math.round((w - 4) * MathHelper.clamp(progress, 0f, 1f));
        if (fill > 0) {
            ctx.fillGradient(x + 2, y + 2, x + 2 + fill, y + h - 2, argb(GOLD_LIGHT, alpha), argb(GOLD_DARK, alpha));
            // laufender Glanz
            long t = Util.getMeasuringTimeMs();
            int shine = (int) ((t / 6) % (w + 40)) - 20;
            if (shine > 0 && shine < fill) {
                ctx.fill(x + 2 + Math.max(0, shine - 6), y + 2, x + 2 + Math.min(fill, shine + 6), y + h - 2, argb(0xFFFFFF, alpha / 3));
            }
        }
    }
}
