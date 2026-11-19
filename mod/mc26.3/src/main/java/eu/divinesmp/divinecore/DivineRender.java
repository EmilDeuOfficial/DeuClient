package eu.divinesmp.divinecore;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.io.InputStream;

/**
 * Zeichen-Helfer im Divine-Stil (26.3 / GuiGraphicsExtractor):
 * Farben, abgerundete Flächen, animierter Hintergrund, Logo, Fortschrittsbalken.
 */
public final class DivineRender {
    private DivineRender() {}

    public static final int GOLD = 0x22C55E;
    public static final int GOLD_LIGHT = 0x86EFAC;
    public static final int GOLD_DARK = 0x14532D;
    public static final int VIOLET = 0x16A34A;
    public static final int VIOLET_MID = 0x14532D;
    public static final int VIOLET_DARK = 0x0A1F12;
    public static final int NIGHT = 0x050706;
    public static final int TEXT = 0xEEF8F1;
    public static final int TEXT_DIM = 0xA3BFAE;

    public static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/emblem");
    public static final Identifier WORDMARK = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/wordmark");
    public static final int EMBLEM_SIZE = 256;
    public static final int WORDMARK_W = 1024;
    public static final int WORDMARK_H = 187;

    private static boolean texturesLoaded;

    /** Textur mit weicher (linearer) Filterung, damit das Logo beim Verkleinern nicht flimmert */
    private static final class SmoothTexture extends DynamicTexture {
        SmoothTexture(String name, NativeImage image) {
            super(() -> name, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    public static int argb(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    public static float pulse(long periodMs) {
        double t = (System.currentTimeMillis() % periodMs) / (double) periodMs;
        return (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
    }

    /** Lädt Logo/Schriftzug direkt aus dem Mod-JAR (auch während des ersten Ladebildschirms verfügbar). */
    public static void ensureTextures() {
        if (texturesLoaded) return;
        texturesLoaded = true;
        register(EMBLEM, "/assets/divinecore/textures/gui/emblem.png");
        register(WORDMARK, "/assets/divinecore/textures/gui/wordmark.png");
    }

    private static final java.util.Map<String, Identifier> ICONS = new java.util.HashMap<>();

    /** Linien-Icon (weich gefiltert) – wird beim ersten Gebrauch aus dem Mod-JAR geladen */
    public static Identifier icon(String name) {
        return ICONS.computeIfAbsent(name, n -> {
            Identifier id = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/icon/" + n);
            register(id, "/assets/divinecore/textures/gui/icons/" + n + ".png");
            return id;
        });
    }

    private static void register(Identifier id, String path) {
        try (InputStream in = DivineRender.class.getResourceAsStream(path)) {
            if (in == null) return;
            Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), NativeImage.read(in)));
        } catch (Exception e) {
            DivineCore.LOGGER.warn("Textur {} konnte nicht geladen werden", path, e);
        }
    }

    /* ---------------- Formen ---------------- */

    /*
     * Kantengeglaettete Rundungen: Nur die Ecken werden als weich gefilterte Textur gezeichnet
     * (vorberechnete Kreise in mehreren Groessen, Rand mit 1 px Verlauf), alles Gerade per fill().
     * So gibt es keine Pixeltreppen, auch bei grosser GUI-Skalierung.
     */
    private static final int[] CORNER_SIZES = {4, 8, 16, 32, 64, 128};
    private static final Identifier[] CORNER_IDS = new Identifier[CORNER_SIZES.length];

    private static Identifier cornerTexture(int idx) {
        if (CORNER_IDS[idx] == null) {
            int rad = CORNER_SIZES[idx];
            NativeImage img = new NativeImage(2 * rad, 2 * rad, true);
            for (int py = 0; py < 2 * rad; py++) {
                for (int px = 0; px < 2 * rad; px++) {
                    // 4x4 Supersampling fuer saubere Kanten
                    int hit = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double dx = px + (sx + 0.5) / 4.0 - rad, dy = py + (sy + 0.5) / 4.0 - rad;
                            if (dx * dx + dy * dy <= (double) rad * rad) hit++;
                        }
                    }
                    img.setPixel(px, py, ((hit * 255 / 16) << 24) | 0xFFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/corner/" + rad);
            Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), img));
            CORNER_IDS[idx] = id;
        }
        return CORNER_IDS[idx];
    }

    /** Vier Ecken einer abgerundeten Flaeche (obere mit topColor, untere mit bottomColor) */
    private static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int topColor, int bottomColor) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int idx = CORNER_SIZES.length - 1;
        for (int i = 0; i < CORNER_SIZES.length; i++) {
            if (CORNER_SIZES[i] >= r * scale) { idx = i; break; }
        }
        Identifier tex = cornerTexture(idx);
        int c = CORNER_SIZES[idx], t = 2 * c;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, r, r, c, c, t, t, topColor);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, (float) c, 0f, r, r, c, c, t, t, topColor);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0f, (float) c, r, r, c, c, t, t, bottomColor);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, (float) c, (float) c, r, r, c, c, t, t, bottomColor);
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
        corners(g, x, y, w, h, r, color, color);
    }

    public static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        int t0 = lerpArgb(top, bottom, r / 2f / h), t1 = lerpArgb(top, bottom, r / (float) h);
        int b0 = lerpArgb(top, bottom, (h - r) / (float) h), b1 = lerpArgb(top, bottom, (h - r / 2f) / h);
        g.fillGradient(x + r, y, x + w - r, y + r, top, t1);
        g.fillGradient(x, y + r, x + w, y + h - r, t1, b0);
        g.fillGradient(x + r, y + h - r, x + w - r, y + h, b0, bottom);
        corners(g, x, y, w, h, r, t0, b1);
    }

    public static int lerpArgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | lerpRgb(a, b, t);
    }

    /** Divine-Karte: dunkles Violett, goldener Rand, heller Schimmer oben */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int alpha) {
        roundRect(g, x - 1, y - 1, w + 2, h + 2, 7, argb(GOLD, alpha / 3));
        roundGradient(g, x, y, w, h, 6, argb(0x10261A, alpha * 235 / 255), argb(0x050A07, alpha * 235 / 255));
        g.fill(x + 6, y + 1, x + w - 6, y + 2, argb(0xFFFFFF, alpha / 8));
    }

    /* ---------------- Logo ---------------- */

    public static void drawEmblem(GuiGraphicsExtractor g, int x, int y, int size, int alpha) {
        ensureTextures();
        drawScaled(g, EMBLEM, x, y, size / (float) EMBLEM_SIZE, EMBLEM_SIZE, EMBLEM_SIZE, alpha);
    }

    public static int drawWordmark(GuiGraphicsExtractor g, int x, int y, int width, int alpha) {
        ensureTextures();
        float s = width / (float) WORDMARK_W;
        drawScaled(g, WORDMARK, x, y, s, WORDMARK_W, WORDMARK_H, alpha);
        return Math.round(WORDMARK_H * s);
    }

    private static void drawScaled(GuiGraphicsExtractor g, Identifier tex, int x, int y, float scale, int tw, int th, int alpha) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, tw, th, tw, th, argb(0xFFFFFF, alpha));
        g.pose().popMatrix();
    }

    /* ---------------- Hintergründe ---------------- */

    /** Animierter Divine-Hintergrund für Menüs außerhalb einer Welt */
    public static void menuBackground(GuiGraphicsExtractor g, int w, int h, int alpha) {
        long t = System.currentTimeMillis();
        float pulse = pulse(5200);
        int top = lerpRgb(0x07120B, 0x0B1F12, pulse);
        int mid = lerpRgb(0x14532D, 0x14532D, 1f - pulse);
        g.fillGradient(0, 0, w, h / 2, argb(top, alpha), argb(mid, alpha));
        g.fillGradient(0, h / 2, w, h, argb(mid, alpha), argb(NIGHT, alpha));

        // goldener Lichtstreifen am Horizont
        int glowA = (int) (alpha * (0.18f + 0.10f * pulse));
        g.fillGradient(0, h / 2, w, h / 2 + h / 6, argb(GOLD, 0), argb(GOLD, glowA / 2));
        g.fillGradient(0, h / 2 + h / 6, w, h / 2 + h / 3, argb(GOLD, glowA / 2), argb(GOLD, 0));

        // blockige Hügel (Minecraft-Stil), zwei Ebenen mit leichtem Parallax
        int shift = (int) ((t / 120) % 32);
        for (int layer = 0; layer < 2; layer++) {
            int ground = h - h / (layer == 0 ? 4 : 6);
            int color = layer == 0 ? 0x0A1F12 : 0x07120B;
            int off = layer == 0 ? shift / 2 : shift;
            for (int x = -32, i = 0; x < w + 32; x += 16, i++) {
                int bump = (int) ((Mth.sin(i * 0.55f + layer * 2f) + Mth.cos(i * 0.23f + 1f + layer)) * (10 - layer * 3));
                g.fill(x - off, ground + bump, x - off + 16, h, argb(color, Math.min(alpha, 235)));
            }
        }

        // aufsteigende Goldpartikel
        if (DivineConfig.get().particles) {
            for (int i = 0; i < 46; i++) {
                float speed = 0.012f + (i % 7) * 0.004f;
                float px = (((i * 97.13f) % 1f + (i * 0.6180339f) % 1f) % 1f) * w + Mth.sin((t / 900f) + i) * 6f;
                float py = h - ((t * speed + i * 211f) % (h + 40f)) + 20f;
                int size = 1 + (i % 3);
                float life = Mth.clamp(1f - (h - py) / (float) h, 0f, 1f);
                int a = (int) (alpha * 0.9f * life);
                g.fill((int) px, (int) py, (int) px + size, (int) py + size, argb(i % 4 == 0 ? GOLD_LIGHT : GOLD, a));
            }
        }
        g.fillGradient(0, h - h / 3, w, h, argb(0, 0), argb(0, alpha / 2));
    }

    /** Divine-Overlay für das Pause-Menü (Welt bleibt dahinter sichtbar) */
    public static void pauseOverlay(GuiGraphicsExtractor g, int w, int h) {
        g.fillGradient(0, 0, w, h, argb(VIOLET_DARK, 185), argb(NIGHT, 225));
        g.fill(0, 0, w, 1, argb(GOLD, 120));
        g.fill(0, h - 1, w, h, argb(GOLD, 120));
    }

    /** Divine-Fortschrittsbalken mit laufendem Glanz */
    public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int alpha) {
        roundRect(g, x, y, w, h, h / 2, argb(GOLD, alpha / 2));
        roundRect(g, x + 1, y + 1, w - 2, h - 2, (h - 2) / 2, argb(0x0A140E, alpha));
        int fill = Math.round((w - 4) * Mth.clamp(progress, 0f, 1f));
        if (fill > 2) {
            roundGradient(g, x + 2, y + 2, fill, h - 4, (h - 4) / 2, argb(GOLD_LIGHT, alpha), argb(GOLD_DARK, alpha));
            int shine = (int) ((System.currentTimeMillis() / 6) % (w + 40)) - 20;
            if (shine > 0 && shine < fill) {
                g.fill(x + 2 + Math.max(0, shine - 6), y + 2, x + 2 + Math.min(fill, shine + 6), y + h - 2, argb(0xFFFFFF, alpha / 3));
            }
        }
    }
}
