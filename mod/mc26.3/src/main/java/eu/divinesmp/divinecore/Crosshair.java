package eu.divinesmp.divinecore;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Eigenes Fadenkreuz in Divine-Farben (ersetzt das Vanilla-Fadenkreuz, wenn aktiviert). */
public final class Crosshair {
    private Crosshair() {}

    public static final String[] STYLES = {"Kreuz", "Punkt", "Kreuz + Punkt", "Kreis", "Pfeil"};
    public static final String[] COLOR_NAMES = {"Weiß", "Gold", "Violett", "Grün", "Rot", "Cyan"};
    public static final int[] COLORS = {0xFFFFFFFF, 0xFF22C55E, 0xFFB17BFF, 0xFF5CFF9D, 0xFFFF5C72, 0xFF4FD1FF};

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
            DivineConfig cfg = DivineConfig.get();
            Minecraft mc = Minecraft.getInstance();
            if (!cfg.customCrosshair) {
                original.extractRenderState(g, delta);
                return;
            }
            if (!mc.options.getCameraType().isFirstPerson()) return;
            draw(g, g.guiWidth() / 2, g.guiHeight() / 2, cfg.crosshairStyle, COLORS[Math.floorMod(cfg.crosshairColor, COLORS.length)]);
        });
    }

    /** Zeichnet das Fadenkreuz zentriert um (cx, cy) – auch für die Vorschau im Mod-Menü */
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
