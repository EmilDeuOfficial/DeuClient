package eu.deusmp.deucore;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Eigenes Fadenkreuz in Deu-Farben (ersetzt das Vanilla-Fadenkreuz, wenn aktiviert). */
public final class Crosshair {
    private Crosshair() {}

    public static final String[] STYLES = {"Kreuz", "Punkt", "Kreuz + Punkt", "Kreis", "Pfeil", "Eigene"};
    public static final String[] COLOR_NAMES = {"Weiß", "Gold", "Violett", "Grün", "Rot", "Cyan", "Eigene"};
    public static final int[] COLORS = {0xFFFFFFFF, 0xFFFFC83D, 0xFFB17BFF, 0xFF2EFF7A, 0xFFFF5C72, 0xFF4FD1FF};

    /** Kantenlaenge der selbst gezeichneten Form */
    public static final int GRID = 11;

    /** Aktuelle Farbe (Auswahl oder eigene Farbe) */
    public static int colorOf(DeuConfig cfg) {
        int idx = Math.floorMod(cfg.crosshairColor, COLOR_NAMES.length);
        return idx >= COLORS.length ? 0xFF000000 | cfg.crosshairCustomColor : COLORS[idx];
    }

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
            DeuConfig cfg = DeuConfig.get();
            Minecraft mc = Minecraft.getInstance();
            if (!cfg.customCrosshair) {
                original.extractRenderState(g, delta);
                return;
            }
            if (!mc.options.getCameraType().isFirstPerson()) return;
            draw(g, g.guiWidth() / 2, g.guiHeight() / 2, cfg.crosshairStyle, colorOf(cfg));
        });
    }

    /** Zeichnet das Fadenkreuz zentriert um (cx, cy) - auch für die Vorschau im Mod-Menü */
    public static void draw(GuiGraphicsExtractor g, int cx, int cy, int style, int color) {
        Theme.paused = true;
        try {
            drawShape(g, cx, cy, style, color);
        } finally {
            Theme.paused = false;
        }
    }

    /** Pixel des Kreisrings (ein Viertel; gespiegelt ergibt es einen gleichmaessigen Kreis mit Radius 4) */
    private static final int[][] RING = {{0, 4}, {1, 4}, {2, 3}, {3, 3}, {3, 2}, {4, 1}, {4, 0}};

    /*
     * Alle Formen liegen punktsymmetrisch um das Pixel (cx, cy). Zuerst wird jede Form als Pixelmuster gesammelt und
     * mit einem gleichmaessigen Schatten unterlegt, dann in der Farbe gezeichnet.
     */
    private static void drawShape(GuiGraphicsExtractor g, int cx, int cy, int style, int color) {
        final int half = GRID / 2, size = GRID;
        boolean[] on = new boolean[size * size];
        java.util.function.BiConsumer<Integer, Integer> set = (x, y) -> on[(y + half) * size + x + half] = true;
        int s = Math.floorMod(style, STYLES.length);
        if (s == 0 || s == 2) { // Kreuz mit Luecke
            for (int i = 2; i <= 5; i++) {
                set.accept(i, 0);
                set.accept(-i, 0);
                set.accept(0, i);
                set.accept(0, -i);
            }
        }
        if (s == 1) { // Punkt, 3 x 3
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) set.accept(x, y);
        }
        if (s == 2) set.accept(0, 0);
        if (s == 3) { // Kreis mit Mittelpunkt
            for (int[] p : RING) {
                set.accept(p[0], p[1]);
                set.accept(-p[0], p[1]);
                set.accept(p[0], -p[1]);
                set.accept(-p[0], -p[1]);
            }
            set.accept(0, 0);
        }
        if (s == 4) { // Pfeil, Spitze in der Mitte
            for (int i = 0; i <= 4; i++) {
                set.accept(i, i);
                set.accept(-i, i);
            }
        }
        if (s == 5) {
            String px = DeuConfig.get().crosshairPixels;
            if (px != null && px.length() == size * size) {
                for (int i = 0; i < size * size; i++) on[i] = px.charAt(i) == '1';
            }
        }
        // Schatten: jedes freie Nachbarpixel genau einmal (auch ueber den Rand des Musters hinaus)
        for (int y = -half - 1; y <= half + 1; y++) {
            for (int x = -half - 1; x <= half + 1; x++) {
                if (at(on, x, y)) continue;
                if (at(on, x - 1, y) || at(on, x + 1, y) || at(on, x, y - 1) || at(on, x, y + 1)) {
                    g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, 0x80000000);
                }
            }
        }
        for (int y = -half; y <= half; y++) {
            for (int x = -half; x <= half; x++) {
                if (at(on, x, y)) g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
            }
        }
    }

    private static boolean at(boolean[] on, int x, int y) {
        int half = GRID / 2;
        return Math.abs(x) <= half && Math.abs(y) <= half && on[(y + half) * GRID + x + half];
    }
}
