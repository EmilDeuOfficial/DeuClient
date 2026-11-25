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
        int shadow = 0x88000000;
        switch (Math.floorMod(style, STYLES.length)) {
            case 0 -> { cross(g, cx, cy, shadow, 1); cross(g, cx, cy, color, 0); }
            case 1 -> { g.fill(cx - 2, cy - 2, cx + 2, cy + 2, shadow); g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color); }
            case 2 -> { cross(g, cx, cy, color, 0); g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color); g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color); }
            case 3 -> {
                for (int a = 0; a < 360; a += 15) {
                    double r = Math.toRadians(a);
                    int x = cx + (int) Math.round(Math.cos(r) * 4), y = cy + (int) Math.round(Math.sin(r) * 4);
                    g.fill(x, y, x + 1, y + 1, color);
                }
                g.fill(cx, cy, cx + 1, cy + 1, color);
            }
            case 5 -> {
                String px = DeuConfig.get().crosshairPixels;
                int n = GRID;
                if (px != null && px.length() == n * n) {
                    for (int i = 0; i < n * n; i++) {
                        if (px.charAt(i) == '1') {
                            int x = cx - n / 2 + i % n, y = cy - n / 2 + i / n;
                            g.fill(x, y, x + 1, y + 1, color);
                        }
                    }
                }
            }
            default -> {
                for (int i = 0; i < 5; i++) {
                    g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                }
            }
        }
    }

    /** Kreuz mit Lücke in der Mitte */
    private static void cross(GuiGraphicsExtractor g, int cx, int cy, int color, int grow) {
        g.fill(cx - 5 - grow, cy - grow, cx - 1 + grow, cy + 1 + grow, color);
        g.fill(cx + 2 - grow, cy - grow, cx + 6 + grow, cy + 1 + grow, color);
        g.fill(cx - grow, cy - 5 - grow, cx + 1 + grow, cy - 1 + grow, color);
        g.fill(cx - grow, cy + 2 - grow, cx + 1 + grow, cy + 6 + grow, color);
    }
}
