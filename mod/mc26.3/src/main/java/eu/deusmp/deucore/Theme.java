package eu.deusmp.deucore;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Themefarbe: alle DeuClient-Gruentoene werden beim Zeichnen auf die gewaehlte Farbe gedreht (Helligkeit bleibt).
 * Quelle: Einstellung im Mod-Menue, bei "Wie Launcher" die vom Launcher uebergebene Farbe (-Ddeuclient.theme).
 */
public final class Theme {
    private Theme() {}

    public static final String[] NAMES = {"Wie Launcher", "Grün", "Blau", "Violett", "Rot", "Orange", "Gelb", "Pink", "Türkis", "Weiß", "Eigene"};
    private static final int[] PRESETS = {0, 0x2EFF7A, 0x3B9DFF, 0x9B6BFF, 0xFF4D5E, 0xFF8A2E, 0xFFD12E, 0xFF5CB8, 0x2EE6D6, 0xE8ECEF, 0};
    public static final int CUSTOM = NAMES.length - 1;
    private static final float BASE_HUE = 142f / 360f;

    private static boolean loaded;
    /** Zaehlt hoch, wenn sich die Farbe aendert (dann muessen Texturen neu geladen werden) */
    public static int revision;
    private static boolean identity = true;
    private static float shift, saturation = 1f;
    /** true, solange etwas in seiner eigenen Farbe bleiben soll (Fadenkreuz) */
    public static boolean paused;

    /** Nach jeder Aenderung der Einstellung aufrufen */
    public static void refresh() {
        DeuConfig cfg = DeuConfig.get();
        int mode = Math.max(0, Math.min(CUSTOM, cfg.themeMode));
        float hue = BASE_HUE;
        saturation = 1f;
        if (mode == CUSTOM) {
            hue = Math.floorMod(cfg.themeHue, 360) / 360f;
        } else {
            int rgb = mode == 0 ? launcherColor() : PRESETS[mode];
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            if (hsb[1] < 0.15f) saturation = 0.08f; // Weiss/Grau
            else hue = hsb[0];
        }
        if (loaded && Math.abs(shift - (hue - BASE_HUE)) > 0.001f || loaded && saturation != lastSaturation) revision++;
        lastSaturation = saturation;
        shift = hue - BASE_HUE;
        identity = Math.abs(shift) < 0.004f && saturation == 1f;
        loaded = true;
        DeuRender.reloadTextures();
    }

    private static float lastSaturation = 1f;

    /** Alle Gruentoene eines geladenen Bildes in die Themefarbe drehen */
    public static void recolor(NativeImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) img.setPixel(x, y, image(img.getPixel(x, y)));
        }
    }

    /** true: die Minecraft-Textur wird vom DeuClient ersetzt (nur diese werden umgefaerbt, nicht das Server-Platzhalterbild) */
    public static boolean ownTexture(String namespace, String path) {
        return namespace.equals("minecraft") && path.startsWith("textures/gui/")
                && Theme.class.getResource("/assets/minecraft/" + path) != null;
    }

    private static int launcherColor() {
        String s = System.getProperty("deuclient.theme", "");
        if (s.matches("#[0-9a-fA-F]{6}")) return Integer.parseInt(s.substring(1), 16);
        return 0x2EFF7A;
    }

    /** Farbe einer Zeichenoperation: nur die DeuClient-Gruentoene (Farbton 130-152 Grad) werden umgefaerbt */
    public static int c(int argb) {
        return map(argb, 130f, 152f, 1f);
    }

    /**
     * true, wenn beim Mischen dieser beiden Farben vorab umgefaerbt werden muss: eine ist ein Themegruen, die andere
     * nicht. Die Zwischentoene wuerden sonst nicht mehr als Gruen erkannt (gruene Ecken, halb gefaerbte Verlaeufe).
     */
    public static boolean mixed(int a, int b) {
        return (c(a) != a) != (c(b) != b);
    }

    /** Pixel eines DeuClient-Bildes (Emblem, Schriftzug): alle Gruentoene */
    public static int image(int argb) {
        // Abweichungen vom Grundton stauchen: der Schriftzug soll in der Themefarbe selbst erscheinen
        return map(argb, 75f, 180f, 0.25f);
    }

    private static int map(int argb, float from, float to, float spread) {
        if (!loaded) refresh();
        if (identity || paused) return argb;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if (g <= r || g < b) return argb; // kein Gruen
        int max = g, min = Math.min(r, b);
        float sat = (max - min) / (float) max;
        if (sat < 0.15f) return argb;
        float d = max - min;
        float deg = 120f + 60f * (b - r) / d;
        if (deg < from || deg > to) return argb;
        float hue = BASE_HUE + (deg / 360f - BASE_HUE) * spread + shift;
        int rgb = java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), Math.min(1f, sat * saturation), max / 255f);
        return (argb & 0xFF000000) | (rgb & 0xFFFFFF);
    }
}
