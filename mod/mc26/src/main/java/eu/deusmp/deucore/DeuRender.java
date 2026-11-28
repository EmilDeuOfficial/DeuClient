package eu.deusmp.deucore;

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
 * Zeichen-Helfer im Deu-Stil (26.3 / GuiGraphicsExtractor):
 * Farben, abgerundete Flächen, animierter Hintergrund, Logo, Fortschrittsbalken.
 */
public final class DeuRender {
    private DeuRender() {}

    public static final int GOLD = 0x2EFF7A;
    public static final int GOLD_LIGHT = 0x9FFFC0;
    public static final int GOLD_DARK = 0x20242C;
    public static final int VIOLET = 0x1FD35F;
    public static final int VIOLET_MID = 0x20242C;
    public static final int VIOLET_DARK = 0x15171C;
    public static final int NIGHT = 0x050706;
    public static final int TEXT = 0xEEF8F1;
    public static final int TEXT_DIM = 0xA3BFAA;

    public static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/emblem");
    public static final Identifier WORDMARK = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/wordmark");
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
        if (Theme.mixed(a, b)) {
            a = Theme.c(a);
            b = Theme.c(b);
        }
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
        register(EMBLEM, "/assets/deucore/textures/gui/emblem.png");
        register(WORDMARK, "/assets/deucore/textures/gui/wordmark.png");
    }

    private static final java.util.Map<String, Identifier> ICONS = new java.util.HashMap<>();

    /** Linien-Icon (weich gefiltert) - wird beim ersten Gebrauch aus dem Mod-JAR geladen */
    public static Identifier icon(String name) {
        return ICONS.computeIfAbsent(name, n -> {
            Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/icon/" + n);
            register(id, "/assets/deucore/textures/gui/icons/" + n + ".png");
            return id;
        });
    }

    private static final java.util.Map<String, float[]> HD_SRC = new java.util.HashMap<>();
    private static final java.util.Map<String, Identifier> HD_PX = new java.util.HashMap<>();
    private static final int HD_SIZE = 256;

    /** Alpha-Kanal der weissen 256-px-Quelle (icons_hd) oder null */
    private static float[] hdSource(String name) {
        if (HD_SRC.containsKey(name)) return HD_SRC.get(name);
        float[] alpha = null;
        try (InputStream in = DeuRender.class.getResourceAsStream("/assets/deucore/textures/gui/icons_hd/" + name + ".png")) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                if (img.getWidth() == HD_SIZE && img.getHeight() == HD_SIZE) {
                    alpha = new float[HD_SIZE * HD_SIZE];
                    for (int y = 0; y < HD_SIZE; y++) {
                        for (int x = 0; x < HD_SIZE; x++) alpha[y * HD_SIZE + x] = (img.getPixel(x, y) >>> 24) & 0xFF;
                    }
                }
                img.close();
            }
        } catch (Exception e) {
            DeuCore.LOGGER.warn("Icon {} konnte nicht geladen werden", name, e);
        }
        HD_SRC.put(name, alpha);
        return alpha;
    }

    /**
     * Weisses Icon exakt in px x px Bildschirm-Pixeln: die hochaufloesende Quelle wird per Flaechenmittel
     * verkleinert, so liegt jedes Textur-Pixel genau auf einem Bildschirm-Pixel (kein Flimmern, keine Treppen).
     */
    public static Identifier iconPx(String name, int px) {
        String key = name + "@" + px;
        Identifier cached = HD_PX.get(key);
        if (cached != null) return cached;
        float[] src = hdSource(name);
        if (src == null) return null;
        NativeImage out = new NativeImage(px, px, true);
        double k = (double) HD_SIZE / px;
        for (int y = 0; y < px; y++) {
            double y0 = y * k, y1 = (y + 1) * k;
            for (int x = 0; x < px; x++) {
                double x0 = x * k, x1 = (x + 1) * k;
                double sum = 0, wsum = 0;
                for (int sy = (int) Math.floor(y0); sy < Math.min(HD_SIZE, (int) Math.ceil(y1)); sy++) {
                    double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) Math.floor(x0); sx < Math.min(HD_SIZE, (int) Math.ceil(x1)); sx++) {
                        double wgt = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        sum += src[sy * HD_SIZE + sx] * wgt;
                        wsum += wgt;
                    }
                }
                int a = wsum > 0 ? (int) Math.round(sum / wsum) : 0;
                out.setPixel(x, y, (Math.max(0, Math.min(255, a)) << 24) | 0xFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/icon_px/" + name + "_" + px);
        Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), out));
        HD_PX.put(key, id);
        return id;
    }

    private static final java.util.Map<String, Identifier> UI_TEX = new java.util.HashMap<>();

    /** Weich gefilterte Vorschau-Textur fuer die Kosmetik-Menues (kind = cape / wings) */
    public static Identifier uiTexture(String kind, String name) {
        return UI_TEX.computeIfAbsent(kind + "_" + name, k -> {
            Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/ui/" + k);
            register(id, "/assets/deucore/textures/cosmetics/ui/" + k + ".png");
            return id;
        });
    }

    /** Emblem und Schriftzug neu laden (Themefarbe geaendert) */
    static void reloadTextures() { texturesLoaded = false; }

    private static void register(Identifier id, String path) {
        try (InputStream in = DeuRender.class.getResourceAsStream(path)) {
            if (in == null) return;
            NativeImage img = NativeImage.read(in);
            // nur Emblem und Schriftzug folgen der Themefarbe; Cape- und Fluegel-Vorschauen behalten ihre Farben
            if (id.equals(EMBLEM) || id.equals(WORDMARK)) Theme.recolor(img);
            Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), img));
        } catch (Exception e) {
            DeuCore.LOGGER.warn("Textur {} konnte nicht geladen werden", path, e);
        }
    }

    /* ---------------- Formen ---------------- */

    /*
     * Kantengeglaettete Rundungen: Nur die Ecken werden als weich gefilterte Textur gezeichnet
     * (vorberechnete Kreise in mehreren Groessen, Rand mit 1 px Verlauf), alles Gerade per fill().
     * So gibt es keine Pixeltreppen, auch bei grosser GUI-Skalierung.
     */
    private static final int MAX_CORNER = 256;
    private static final java.util.Map<Integer, Identifier> CORNER_IDS = new java.util.HashMap<>();

    /** Gesamtfaktor GUI-Pixel -> Bildschirm-Pixel (GUI-Skalierung mal aktuelle Matrix-Skalierung) */
    public static double totalScale(GuiGraphicsExtractor g) {
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        var p = g.pose();
        double ps = Math.sqrt((double) p.m00() * p.m00() + (double) p.m01() * p.m01());
        return ps > 0.01 ? gs * ps : gs;
    }

    /** Radius der Ecke in Bildschirm-Pixeln (die Textur hat genau diese Groesse: 1 Textur-Pixel = 1 Bildschirm-Pixel) */
    private static int cornerPx(int r, double scale) {
        return Math.max(1, Math.min(MAX_CORNER, (int) Math.round(r * scale)));
    }

    private static Identifier cornerTexture(int rad) {
        Identifier cached = CORNER_IDS.get(rad);
        if (cached != null) return cached;
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
                img.setPixel(px, py, (Math.round(hit * 255f / 16f) << 24) | 0xFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/corner/" + rad);
        Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), img));
        CORNER_IDS.put(rad, id);
        return id;
    }

    /** Vier Ecken einer abgerundeten Flaeche (obere mit topColor, untere mit bottomColor) */
    private static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int topColor, int bottomColor) {
        int c = cornerPx(r, totalScale(g)), t = 2 * c;
        Identifier tex = cornerTexture(c);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, r, r, c, c, t, t, topColor);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, (float) c, 0f, r, r, c, c, t, t, topColor);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0f, (float) c, r, r, c, c, t, t, bottomColor);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, (float) c, (float) c, r, r, c, c, t, t, bottomColor);
    }

    /** true: GUI-Koordinaten liegen nicht auf ganzen Bildschirm-Pixeln (krumme GUI- oder HUD-Groesse) */
    private static boolean offGrid(GuiGraphicsExtractor g) {
        var p = g.pose();
        if (Math.abs(p.m01()) > 1e-4f || Math.abs(p.m10()) > 1e-4f) return false; // gedreht: so zeichnen
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        return frac(totalScale(g)) || frac(p.m20() * gs) || frac(p.m21() * gs);
    }

    private static boolean frac(double v) { return Math.abs(v - Math.rint(v)) > 0.01; }

    private interface PxShape { void draw(int x, int y, int w, int h, double scale); }

    /** Rechnet das Rechteck in Bildschirm-Pixel um und zeichnet dort: Ecken, Rand und Flaeche passen exakt zusammen */
    private static void inPixels(GuiGraphicsExtractor g, int x, int y, int w, int h, PxShape shape) {
        var p = g.pose();
        double gs = Minecraft.getInstance().getWindow().getGuiScale(), s = totalScale(g);
        int x0 = (int) Math.round((p.m20() + x * p.m00()) * gs), x1 = (int) Math.round((p.m20() + (x + w) * p.m00()) * gs);
        int y0 = (int) Math.round((p.m21() + y * p.m11()) * gs), y1 = (int) Math.round((p.m21() + (y + h) * p.m11()) * gs);
        g.pose().pushMatrix();
        g.pose().identity();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        shape.draw(x0, y0, x1 - x0, y1 - y0, s);
        g.pose().popMatrix();
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        if (offGrid(g)) {
            inPixels(g, x, y, w, h, (px, py, pw, ph, s) -> rectOnGrid(g, px, py, pw, ph, (int) Math.round(r * s), color));
            return;
        }
        rectOnGrid(g, x, y, w, h, r, color);
    }

    private static void rectOnGrid(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
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
        if (offGrid(g)) {
            inPixels(g, x, y, w, h, (px, py, pw, ph, s) -> gradientOnGrid(g, px, py, pw, ph, (int) Math.round(r * s), top, bottom));
            return;
        }
        gradientOnGrid(g, x, y, w, h, r, top, bottom);
    }

    private static void gradientOnGrid(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
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

    /** Abgerundete Flaeche mit waagerechtem Verlauf (beide Farben deckend) */
    public static void roundGradientH(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int left, int right) {
        r = Math.min(r, Math.min(w, h) / 2);
        roundRect(g, x, y, w, h, r, right);
        roundRect(g, x, y, 2 * r + 1, h, r, left);
        for (int i = r; i < w - r; i++) g.fill(x + i, y, x + i + 1, y + h, lerpArgb(left, right, (i - r) / (float) Math.max(1, w - 2 * r)));
    }

    private static final java.util.Map<Long, Identifier> PILL_IDS = new java.util.HashMap<>();

    /** Abgerundeter Strich (Kapsel) in Bildschirm-Pixeln, 4x4-Supersampling: saubere runde Enden */
    private static Identifier pillTexture(int w, int h) {
        long key = ((long) w << 32) | h;
        Identifier cached = PILL_IDS.get(key);
        if (cached != null) return cached;
        NativeImage img = new NativeImage(w, h, true);
        double r = Math.min(w, h) / 2.0;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int hit = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        double x = px + (sx + 0.5) / 4.0, y = py + (sy + 0.5) / 4.0;
                        double cy = y < r ? r : (y > h - r ? h - r : y);
                        double cx = x < r ? r : (x > w - r ? w - r : x);
                        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) hit++;
                    }
                }
                img.setPixel(px, py, (Math.round(hit * 255f / 16f) << 24) | 0xFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/pill/" + w + "x" + h);
        Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), img));
        PILL_IDS.put(key, id);
        return id;
    }

    /**
     * Zeichnet einen abgerundeten Strich der Groesse wPx x hPx Bildschirm-Pixel mit der linken oberen Ecke
     * bei (x, y) in GUI-Pixeln (Bruchteile erlaubt, bitte auf Bildschirm-Pixel gerastert uebergeben).
     */
    public static void pill(GuiGraphicsExtractor g, float x, float y, int wPx, int hPx, int color) {
        if (wPx <= 0 || hPx <= 0 || (color >>> 24) == 0) return;
        float inv = (float) (1.0 / totalScale(g));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(inv, inv);
        g.blit(RenderPipelines.GUI_TEXTURED, pillTexture(wPx, hPx), 0, 0, 0f, 0f, wPx, hPx, wPx, hPx, wPx, hPx, color);
        g.pose().popMatrix();
    }

    /* ---------------- Pixelgenaue, abgerundete Bilder ---------------- */

    /**
     * Quellbild (z. B. Server-Icon), das pixelgenau in Bildschirm-Pixeln gezeichnet wird: Es wird per Flaechenmittel
     * (beim Vergroessern bilinear) auf die echte Zielgroesse gerechnet und dort mit glatten, 4x4-supersampelten
     * Ecken maskiert. So sind die Rundungen bei jeder GUI-Skalierung so sauber wie bei den uebrigen Formen.
     */
    public static final class PixelImage {
        private final int w, h;
        private final int[] px;
        private final Identifier id;
        private int built;

        public PixelImage(String key, NativeImage src) {
            this.w = src.getWidth();
            this.h = src.getHeight();
            this.px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) px[y * w + x] = src.getPixel(x, y);
            }
            src.close();
            this.id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/pximg/" + key);
        }
    }

    /** Zeichnet das Bild size x size GUI-Pixel gross, Ecken mit Radius radiusFrac * Kantenlaenge. */
    public static void drawRounded(GuiGraphicsExtractor g, PixelImage img, float x, float y, int size, double radiusFrac) {
        double k = totalScale(g);
        int d = (int) Math.round(size * k);
        if (d < 2) return;
        if (img.built != d) {
            buildRounded(img, d, radiusFrac);
            img.built = d;
        }
        float inv = (float) (1.0 / k);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(inv, inv);
        g.blit(RenderPipelines.GUI_TEXTURED, img.id, 0, 0, 0f, 0f, d, d, d, d, d, d);
        g.pose().popMatrix();
    }

    private static void buildRounded(PixelImage src, int d, double radiusFrac) {
        int[][] xi = new int[d][], yi = new int[d][];
        float[][] xw = new float[d][], yw = new float[d][];
        axisTaps(src.w, d, xi, xw);
        axisTaps(src.h, d, yi, yw);
        NativeImage out = new NativeImage(d, d, true);
        double r = d * radiusFrac;
        int ri = (int) Math.ceil(r);
        for (int oy = 0; oy < d; oy++) {
            for (int ox = 0; ox < d; ox++) {
                float sa = 0, s0 = 0, s1 = 0, s2 = 0;
                for (int j = 0; j < yi[oy].length; j++) {
                    for (int i = 0; i < xi[ox].length; i++) {
                        float wgt = xw[ox][i] * yw[oy][j];
                        int c = src.px[yi[oy][j] * src.w + xi[ox][i]];
                        float a = ((c >>> 24) & 255) * wgt;
                        sa += a;
                        s0 += a * (c & 255);
                        s1 += a * ((c >> 8) & 255);
                        s2 += a * ((c >> 16) & 255);
                    }
                }
                int alpha = Math.min(255, Math.round(sa));
                int c0 = 0, c1 = 0, c2 = 0;
                if (sa > 0) {
                    c0 = Math.min(255, Math.round(s0 / sa));
                    c1 = Math.min(255, Math.round(s1 / sa));
                    c2 = Math.min(255, Math.round(s2 / sa));
                }
                int ex = Math.min(ox, d - 1 - ox), ey = Math.min(oy, d - 1 - oy);
                if (ex < ri && ey < ri) {
                    int hit = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double dx = ex + (sx + 0.5) / 4.0 - r, dy = ey + (sy + 0.5) / 4.0 - r;
                            if (dx * dx + dy * dy <= r * r) hit++;
                        }
                    }
                    alpha = Math.round(alpha * hit / 16f);
                }
                out.setPixel(ox, oy, (alpha << 24) | (c2 << 16) | (c1 << 8) | c0);
            }
        }
        Minecraft.getInstance().getTextureManager().register(src.id, new SmoothTexture(src.id.toString(), out));
    }

    /** Gewichte pro Zielpixel: Flaechenmittel beim Verkleinern, bilinear beim Vergroessern. */
    private static void axisTaps(int srcN, int dstN, int[][] idx, float[][] wt) {
        double scale = srcN / (double) dstN;
        for (int i = 0; i < dstN; i++) {
            if (scale > 1.0) {
                double a = i * scale, b = (i + 1) * scale;
                int s0 = (int) Math.floor(a), s1 = Math.min(srcN - 1, (int) Math.ceil(b) - 1);
                int n = Math.max(1, s1 - s0 + 1);
                idx[i] = new int[n];
                wt[i] = new float[n];
                for (int s = s0; s < s0 + n; s++) {
                    double ov = Math.min(b, s + 1) - Math.max(a, s);
                    idx[i][s - s0] = Math.min(s, srcN - 1);
                    wt[i][s - s0] = (float) (Math.max(0, ov) / scale);
                }
            } else {
                double u = (i + 0.5) * scale - 0.5;
                int s0 = (int) Math.floor(u);
                double f = u - s0;
                idx[i] = new int[] {Mth.clamp(s0, 0, srcN - 1), Mth.clamp(s0 + 1, 0, srcN - 1)};
                wt[i] = new float[] {(float) (1 - f), (float) f};
            }
        }
    }

    private static final java.util.Map<Integer, Identifier> RING_IDS = new java.util.HashMap<>();

    /** Viertelring-Textur (4x4 Supersampling) mit Radius rad und Dicke thick (in Textur-Pixeln) */
    private static Identifier ringTexture(int rad, int thick) {
        int key = (rad << 8) | thick;
        Identifier cached = RING_IDS.get(key);
        if (cached != null) return cached;
        NativeImage img = new NativeImage(2 * rad, 2 * rad, true);
        double inner = rad - thick;
        for (int py = 0; py < 2 * rad; py++) {
            for (int px = 0; px < 2 * rad; px++) {
                int hit = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        double dx = px + (sx + 0.5) / 4.0 - rad, dy = py + (sy + 0.5) / 4.0 - rad;
                        double d2 = dx * dx + dy * dy;
                        if (d2 <= (double) rad * rad && d2 >= inner * inner) hit++;
                    }
                }
                img.setPixel(px, py, (Math.round(hit * 255f / 16f) << 24) | 0xFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/ring/" + rad + "_" + thick);
        Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(id.toString(), img));
        RING_IDS.put(key, id);
        return id;
    }

    /** Glatte, abgerundete 1-Pixel-Umrandung (GUI-Pixel) - Ecken genauso dick wie die geraden Kanten */
    public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        if (offGrid(g)) {
            inPixels(g, x, y, w, h, (px, py, pw, ph, s) ->
                    outlineOnGrid(g, px, py, pw, ph, (int) Math.round(r * s), Math.max(1, (int) Math.round(s)), color));
            return;
        }
        outlineOnGrid(g, x, y, w, h, r, 1, color);
    }

    /** Rand mit der Dicke t (in Einheiten der aktuellen Matrix) */
    private static void outlineOnGrid(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + t, color);
            g.fill(x, y + h - t, x + w, y + h, color);
            g.fill(x, y + t, x + t, y + h - t, color);
            g.fill(x + w - t, y + t, x + w, y + h - t, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + t, color);
        g.fill(x + r, y + h - t, x + w - r, y + h, color);
        g.fill(x, y + r, x + t, y + h - r, color);
        g.fill(x + w - t, y + r, x + w, y + h - r, color);
        double scale = totalScale(g);
        int c = cornerPx(r, scale), size = 2 * c;
        int thick = Math.max(1, Math.min(c, (int) Math.round(t * scale)));
        Identifier tex = ringTexture(c, thick);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, r, r, c, c, size, size, color);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, (float) c, 0f, r, r, c, c, size, size, color);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0f, (float) c, r, r, c, c, size, size, color);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, (float) c, (float) c, r, r, c, c, size, size, color);
    }

    public static int lerpArgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | lerpRgb(a, b, t);
    }

    /** Deu-Karte: dunkles Violett, goldener Rand, heller Schimmer oben */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int alpha) {
        roundRect(g, x - 1, y - 1, w + 2, h + 2, 7, argb(GOLD, alpha / 3));
        roundGradient(g, x, y, w, h, 6, argb(0x1B1E24, alpha * 235 / 255), argb(0x0C0D10, alpha * 235 / 255));
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

    /** Animierter Deu-Hintergrund für Menüs außerhalb einer Welt */
    public static void menuBackground(GuiGraphicsExtractor g, int w, int h, int alpha) {
        long t = System.currentTimeMillis();
        float pulse = pulse(5200);
        int top = lerpRgb(0x0A0B0E, 0x12151A, pulse);
        int mid = lerpRgb(0x20242C, 0x20242C, 1f - pulse);
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
            int color = layer == 0 ? 0x15171C : 0x0A0B0E;
            int off = layer == 0 ? shift / 2 : shift;
            for (int x = -32, i = 0; x < w + 32; x += 16, i++) {
                int bump = (int) ((Mth.sin(i * 0.55f + layer * 2f) + Mth.cos(i * 0.23f + 1f + layer)) * (10 - layer * 3));
                g.fill(x - off, ground + bump, x - off + 16, h, argb(color, Math.min(alpha, 235)));
            }
        }

        // aufsteigende Goldpartikel
        if (DeuConfig.get().particles) {
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

    /** Deu-Overlay für das Pause-Menü (Welt bleibt dahinter sichtbar) */
    public static void pauseOverlay(GuiGraphicsExtractor g, int w, int h) {
        g.fillGradient(0, 0, w, h, argb(VIOLET_DARK, 185), argb(NIGHT, 225));
        g.fill(0, 0, w, 1, argb(GOLD, 120));
        g.fill(0, h - 1, w, h, argb(GOLD, 120));
    }

    /**
     * Kapsel in Bildschirm-Pixeln (Pose muss bereits auf 1 Einheit = 1 Bildschirm-Pixel skaliert sein).
     * Zeilenweiser Farbverlauf von top nach bottom: Kappen sind Halbkreis-Texturen, Mitte per fill().
     */
    private static void capsulePx(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        if (h <= 0 || (top >>> 24) == 0 && (bottom >>> 24) == 0) return;
        if (w < h) w = h;
        int l = h / 2, r = h - l;
        Identifier tex = pillTexture(h, h);
        if (top == bottom) {
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, l, h, l, h, h, h, top);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, (float) l, 0f, r, h, r, h, h, h, top);
            if (w > h) g.fill(x + l, y, x + w - r, y + h, top);
            return;
        }
        for (int row = 0; row < h; row++) {
            int c = lerpArgb(top, bottom, (row + 0.5f) / h);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + row, 0f, (float) row, l, 1, l, 1, h, h, c);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + row, (float) l, (float) row, r, 1, r, 1, h, h, c);
            if (w > h) g.fill(x + l, y + row, x + w - r, y + row + 1, c);
        }
    }

    /** Ziffern 0-9 und das Prozentzeichen der Minecraft-Schrift: 7 Zeilen mit je 5 Punkten (Bit 4 = links) */
    private static final int[][] PERCENT_GLYPHS = {
            {14, 17, 19, 21, 25, 17, 14}, {4, 12, 4, 4, 4, 4, 31}, {14, 17, 1, 6, 8, 17, 31}, {14, 17, 1, 6, 1, 17, 14},
            {3, 5, 9, 17, 31, 1, 1}, {31, 16, 30, 1, 1, 17, 14}, {6, 8, 16, 30, 17, 17, 14}, {31, 17, 1, 2, 4, 4, 4},
            {14, 17, 17, 14, 17, 17, 14}, {14, 17, 17, 15, 1, 2, 12}, {17, 18, 2, 4, 8, 9, 17}};

    /**
     * Prozentzahl im Aussehen der Minecraft-Schrift samt Schatten, aber nur aus Rechtecken:
     * beim ersten Laden sind bis 26.2 die Text-Shader noch nicht bereit.
     */
    public static void percent(GuiGraphicsExtractor g, int centerX, int y, float progress, int color) {
        String text = Math.round(Mth.clamp(progress, 0f, 1f) * 100) + "%";
        int x = centerX - text.length() * 3;
        int shadow = color & 0xFF000000 | (color & 0xFCFCFC) >> 2;
        for (int pass = 1; pass >= 0; pass--) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int[] rows = PERCENT_GLYPHS[c == '%' ? 10 : c - '0'];
                for (int row = 0; row < rows.length; row++) {
                    for (int col = 0; col < 5; col++) {
                        if ((rows[row] & 16 >> col) == 0) continue;
                        int px = x + i * 6 + col + pass, py = y + row + pass;
                        g.fill(px, py, px + 1, py + 1, pass == 1 ? shadow : color);
                    }
                }
            }
        }
    }

    /** Deu-Fortschrittsbalken: gruen/dunkel, saubere runde Enden, laufender Glanz nur innerhalb der Fuellung */
    public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int alpha) {
        double k = totalScale(g);
        int wPx = Math.max(8, (int) Math.round(w * k));
        int hPx = Math.max(6, (int) Math.round(h * k));
        int x0 = (int) Math.round(x * k), y0 = (int) Math.round(y * k);
        int t = Math.max(1, (int) Math.round(k));
        float inv = (float) (1.0 / k);

        g.pose().pushMatrix();
        g.pose().scale(inv, inv);

        // Rand und Bahn
        capsulePx(g, x0, y0, wPx, hPx, argb(0x2EFF7A, alpha * 3 / 5), argb(0x17A84A, alpha * 3 / 5));
        capsulePx(g, x0 + t, y0 + t, wPx - 2 * t, hPx - 2 * t, argb(0x0C130E, alpha), argb(0x070B08, alpha));

        // Fuellung
        int fx = x0 + 2 * t, fy = y0 + 2 * t, fh = hPx - 4 * t, fMax = wPx - 4 * t;
        float p = Mth.clamp(progress, 0f, 1f);
        if (fh > 1 && p > 0.001f) {
            int fw = Math.max(fh, Math.round(fMax * p));
            capsulePx(g, fx, fy, fw, fh, argb(0x6BFF9E, alpha), argb(0x17A84A, alpha));

            // Glanz: gleiche Kapselform, bleibt komplett in der Fuellung
            int sw = fh * 3;
            if (fw > sw + fh) {
                float ph = (System.currentTimeMillis() % 1800L) / 1800f;
                int sx = fx + Math.round((fw - sw) * ph);
                int sa = (int) (alpha * 0.28f * Mth.sin(ph * (float) Math.PI));
                capsulePx(g, sx, fy, sw, fh, argb(0xFFFFFF, sa), argb(0xFFFFFF, sa));
            }
        }
        g.pose().popMatrix();
    }
}
