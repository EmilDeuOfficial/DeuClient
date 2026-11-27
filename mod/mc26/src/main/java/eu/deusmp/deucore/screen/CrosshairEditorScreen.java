package eu.deusmp.deucore.screen;

import eu.deusmp.deucore.Crosshair;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui.IconButton;
import eu.deusmp.deucore.screen.Ui.IconButton.Style;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Editor fuer das Fadenkreuz: eigene Form (11x11 Pixel, Klicken und Ziehen zum Malen)
 * und eigene Farbe mit Farbwaehler (Farbton-Leiste + Saettigung/Helligkeit).
 */
public class CrosshairEditorScreen extends Screen {
    private static final int N = Crosshair.GRID;
    private static final int RW = 130; // Breite der rechten Spalte
    private static final int SV_H = 54, HUE_H = 10;
    private final Screen parent;
    private final boolean[] px = new boolean[N * N];
    private int paint = -1; // -1 = nicht am Malen, 0 = loeschen, 1 = zeichnen
    private int drag = 0;   // 0 = nichts, 1 = Saettigung/Helligkeit, 2 = Farbton
    private float hue, sat, val;

    public CrosshairEditorScreen(Screen parent) {
        super(Component.literal("Fadenkreuz"));
        this.parent = parent;
        DeuConfig cfg = DeuConfig.get();
        String s = cfg.crosshairPixels;
        if (s != null && s.length() == N * N) {
            for (int i = 0; i < px.length; i++) px[i] = s.charAt(i) == '1';
        }
        int rgb = Crosshair.colorOf(cfg) & 0xFFFFFF;
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
    }

    /* ---------- Farbe ---------- */

    private static int hsvToRgb(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private int currentColor() { return 0xFF000000 | hsvToRgb(hue, sat, val); }

    private void saveColor() {
        DeuConfig cfg = DeuConfig.get();
        cfg.crosshairCustomColor = hsvToRgb(hue, sat, val);
        cfg.crosshairColor = Crosshair.COLORS.length; // = "Eigene"
        DeuConfig.save();
    }

    /* ---------- Layout ---------- */

    private int cell() { return Math.max(6, Math.min(16, (this.height - 150) / N)); }
    private int contentH() { return Math.max(N * cell(), 206); }
    private int panelW() { return N * cell() + 20 + 24 + RW + 20; }
    private int panelH() { return contentH() + 76; }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelY() { return (this.height - panelH()) / 2; }
    private int gridX() { return panelX() + 20; }
    private int gridY() { return panelY() + 56; }
    private int rightX() { return gridX() + N * cell() + 24; }
    private int svY() { return gridY() + 58; }
    private int hueY() { return svY() + SV_H + 6; }

    @Override
    protected void init() {
        int bx = rightX(), by = gridY() + contentH() - 68;
        addRenderableWidget(new IconButton(bx, by, RW, 20, null, Component.literal("Leeren"), b -> { java.util.Arrays.fill(px, false); save(); }));
        addRenderableWidget(new IconButton(bx, by + 24, RW, 20, null, Component.literal("Kreuz als Vorlage"), b -> { template(); save(); }));
        addRenderableWidget(new IconButton(bx, by + 48, RW, 20, Ui.I_CHECK, Component.literal("Fertig"), Style.ACCENT, 0, b -> onClose()));
    }

    private void template() {
        java.util.Arrays.fill(px, false);
        int c = N / 2;
        for (int i = 0; i < N; i++) {
            if (Math.abs(i - c) > 1) { px[c * N + i] = true; px[i * N + c] = true; }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (boolean b : px) { sb.append(b ? '1' : '0'); any |= b; }
        DeuConfig cfg = DeuConfig.get();
        cfg.crosshairPixels = sb.toString();
        if (any) { cfg.crosshairStyle = 5; cfg.customCrosshair = true; }
        DeuConfig.save();
    }

    private int cellAt(double mx, double my) {
        int c = cell();
        int x = (int) Math.floor((mx - gridX()) / c), y = (int) Math.floor((my - gridY()) / c);
        return x >= 0 && y >= 0 && x < N && y < N ? y * N + x : -1;
    }

    private boolean inSv(double mx, double my) { return mx >= rightX() && mx < rightX() + RW && my >= svY() && my < svY() + SV_H; }
    private boolean inHue(double mx, double my) { return mx >= rightX() && mx < rightX() + RW && my >= hueY() && my < hueY() + HUE_H; }

    private void pick(double mx, double my) {
        if (drag == 1) {
            sat = (float) Math.max(0, Math.min(1, (mx - rightX()) / (RW - 1)));
            val = 1f - (float) Math.max(0, Math.min(1, (my - svY()) / (SV_H - 1)));
        } else if (drag == 2) {
            hue = (float) Math.max(0, Math.min(0.999, (mx - rightX()) / (RW - 1)));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        if (inSv(e.x(), e.y())) { drag = 1; pick(e.x(), e.y()); return true; }
        if (inHue(e.x(), e.y())) { drag = 2; pick(e.x(), e.y()); return true; }
        int i = cellAt(e.x(), e.y());
        if (i < 0) return false;
        paint = px[i] ? 0 : 1;
        px[i] = paint == 1;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (drag != 0) { pick(e.x(), e.y()); return true; }
        if (paint >= 0) {
            int i = cellAt(e.x(), e.y());
            if (i >= 0) px[i] = paint == 1;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (drag != 0) { pick(e.x(), e.y()); drag = 0; saveColor(); return true; }
        if (paint >= 0) { paint = -1; save(); return true; }
        return super.mouseReleased(e);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        g.fillGradient(0, 0, this.width, this.height, 0x8007080A, 0xA007080A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int c = cell(), gx = gridX(), gy = gridY();
        int panelX = panelX(), panelY = panelY();
        DeuRender.roundRect(g, panelX, panelY, panelW(), panelH(), 6, Ui.BG);
        Ui.title(g, "Fadenkreuz zeichnen", panelX + 20, panelY + 14, 1.3f, Ui.TEXT);
        Ui.text(g, "Klicken und ziehen zum Malen", panelX + 20, panelY + 32, Ui.TEXT_DIM);
        int color = currentColor();
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                boolean on = px[y * N + x];
                boolean mid = x == N / 2 && y == N / 2;
                DeuRender.roundRect(g, gx + x * c, gy + y * c, c - 1, c - 1, 1, on ? color : mid ? 0xFF23262C : 0xFF16181D);
            }
        }
        // Vorschau in Originalgroesse
        int rx = rightX();
        int vx = rx + (RW - 1) / 2, vy = gy + 22;
        DeuRender.roundRect(g, rx, gy, RW - 1, 45, 5, Ui.CARD);
        StringBuilder sb = new StringBuilder();
        for (boolean b : px) sb.append(b ? '1' : '0');
        String old = DeuConfig.get().crosshairPixels;
        DeuConfig.get().crosshairPixels = sb.toString();
        Crosshair.draw(g, vx, vy, 5, color);
        DeuConfig.get().crosshairPixels = old;

        // Farbwaehler: Saettigung (x) / Helligkeit (y) und Farbton-Leiste
        int sy = svY();
        for (int x = 0; x < RW; x += 2) {
            float s = x / (float) (RW - 1);
            g.fillGradient(rx + x, sy, rx + x + 2, sy + SV_H, 0xFF000000 | hsvToRgb(hue, s, 1f), 0xFF000000);
        }
        for (int x = 0; x < RW; x += 2) {
            g.fill(rx + x, hueY(), rx + x + 2, hueY() + HUE_H, 0xFF000000 | hsvToRgb(x / (float) RW, 1f, 1f));
        }
        DeuRender.roundOutline(g, rx - 1, sy - 1, RW + 2, SV_H + 2, 2, 0x40FFFFFF);
        DeuRender.roundOutline(g, rx - 1, hueY() - 1, RW + 2, HUE_H + 2, 2, 0x40FFFFFF);
        int mx = rx + Math.round(sat * (RW - 1)), my = sy + Math.round((1f - val) * (SV_H - 1));
        DeuRender.roundOutline(g, mx - 3, my - 3, 7, 7, 3, 0xFFFFFFFF);
        int hx = rx + Math.round(hue * (RW - 1));
        DeuRender.roundOutline(g, hx - 2, hueY() - 2, 5, HUE_H + 4, 2, 0xFFFFFFFF);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
