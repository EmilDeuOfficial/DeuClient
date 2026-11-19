package eu.divinesmp.divinecore.screen;

import eu.divinesmp.divinecore.DivineCore;
import eu.divinesmp.divinecore.DivineRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * UI-Bausteine des Divine Clients im cleanen Client-Look:
 * glatte Inter-Schrift, weiße Linien-Icons (eingefärbt), dunkle halbtransparente Flächen.
 */
public final class Ui {
    private Ui() {}

    // ---- Farben
    public static final int BG = 0xEB0A0D0B;
    public static final int CARD = 0xFF0F1410;
    public static final int CARD_HOVER = 0xFF141A16;
    public static final int FIELD = 0xD90A0F0C;
    public static final int FIELD_HOVER = 0xE6141A16;
    public static final int LINE = 0x18FFFFFF;
    public static final int TEXT = 0xFFF3F4F7;
    public static final int TEXT_DIM = 0xFF9097A3;
    public static final int GOLD = 0xFF22C55E;
    public static final int GREEN = 0xFF1C5A2C;
    public static final int GREEN_TEXT = 0xFF7CF29C;

    // ---- Icons (Dateinamen in textures/gui/icons)
    public static final String I_GRID = "grid", I_STAR = "star", I_GEAR = "gear", I_HEART = "heart", I_CLOSE = "close";
    public static final String I_PERSON = "person", I_PEOPLE = "people", I_SEARCH = "search", I_POWER = "power", I_HOME = "home";
    public static final String I_HOURGLASS = "hourglass", I_BUTTON = "button", I_SPARKLE = "sparkle", I_WINDOW = "window";
    public static final String I_INFO = "info", I_TAG = "tag", I_ZOOM = "zoom", I_CROSS = "crosshair", I_CAPE = "cape";
    public static final String I_WINGS = "wings", I_HUD = "hud", I_CHAT = "chat", I_MUSIC = "music", I_EYE = "eye";
    public static final String I_GLOBE = "globe", I_PAINT = "paint", I_MOON = "moon", I_LANGUAGE = "language";
    public static final String I_SERVER = "server", I_CHEV_L = "chevron_left", I_CHEV_R = "chevron_right";
    public static final String I_MINUS = "minus", I_PLUS = "plus", I_CHECK = "check";

    /* ================= Schrift ================= */

    private static final Style UI = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "ui")));
    private static final Style BOLD = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "ui_bold")));

    public static MutableComponent ui(String s) {
        return Component.literal(s).setStyle(UI);
    }

    public static MutableComponent bold(String s) {
        return Component.literal(s).setStyle(BOLD);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int width(String s) {
        return font().width(ui(s));
    }

    public static int boldWidth(String s) {
        return font().width(bold(s));
    }

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font(), ui(s), x, y, color, false);
    }

    public static void boldText(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font(), bold(s), x, y, color, false);
    }

    public static void centered(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        text(g, s, cx - width(s) / 2, y, color);
    }

    /** Skalierter Titel in fetter Schrift */
    public static void title(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        boldText(g, s, 0, 0, color);
        g.pose().popMatrix();
    }

    public static String ellipsize(String s, int maxW) {
        if (width(s) <= maxW) return s;
        while (s.length() > 1 && width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /* ================= Icons ================= */

    public static void icon(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, DivineRender.icon(name), x, y, 0f, 0f, size, size, 64, 64, 64, 64, color);
    }

    public static void iconCentered(GuiGraphicsExtractor g, String name, int cx, int cy, int size, int color) {
        icon(g, name, cx - size / 2, cy - size / 2, size, color);
    }

    /** Zeichnet ein Flügelpaar (Textur + gespiegelte Textur), Wurzeln in der Mitte bei cx */
    public static void wingPair(GuiGraphicsExtractor g, Identifier tex, int cx, int y, int size) {
        // rechts: normal; links: per UV gespiegelt (negative Breite) – GUI verwirft gespiegelte Flächen
        g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - 2, y, 0f, 0f, size, size, 128, 128, 128, 128);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - size + 2, y, 128f, 0f, size, size, -128, 128, 128, 128);
    }

    /* ================= Buttons ================= */

    /**
     * Flacher Button mit Icon und Text. Stile: NORMAL (dunkel, halbtransparent), ACCENT (gold),
     * DANGER (rot, dezent), GHOST (ohne Hintergrund), SQUARE (quadratischer Icon-Button).
     */
    public static class IconButton extends Button {
        public enum Style { NORMAL, ACCENT, DANGER, GHOST }

        private final String icon;
        private final Style style;
        private final int iconColor;
        private boolean selected;
        private boolean boldLabel;

        public IconButton(int x, int y, int w, int h, String icon, Component label, Style style, int iconColor, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.style = style;
            this.iconColor = iconColor;
        }

        public IconButton(int x, int y, int w, int h, String icon, Component label, OnPress onPress) {
            this(x, y, w, h, icon, label, Style.NORMAL, 0, onPress);
        }

        public IconButton selected(boolean s) {
            this.selected = s;
            return this;
        }

        public IconButton bold() {
            this.boldLabel = true;
            return this;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = this.active && isHoveredOrFocused();
            int r = Math.min(4, h / 4);
            boolean dark = false;
            switch (style) {
                case ACCENT -> {
                    DivineRender.roundGradient(g, x, y, w, h, r, hover ? 0xFF4ADE80 : 0xFF22C55E, hover ? 0xFF16A34A : 0xFF15803D);
                    dark = true;
                }
                case DANGER -> DivineRender.roundRect(g, x, y, w, h, r, hover ? 0x99B4233A : 0x55801628);
                case GHOST -> { if (hover) DivineRender.roundRect(g, x, y, w, h, r, 0x18FFFFFF); }
                default -> {
                    if (selected) {
                        DivineRender.roundGradient(g, x, y, w, h, r, 0xFF22C55E, 0xFF15803D);
                        dark = true;
                    } else {
                        if (hover) DivineRender.roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, 0xFF22C55E);
                        DivineRender.roundRect(g, x, y, w, h, r, hover ? FIELD_HOVER : FIELD);
                    }
                }
            }
            int textColor = !this.active ? 0xFF5C6270 : dark ? 0xFF03140A : style == Style.DANGER ? 0xFFFF6B7D : TEXT;
            int ic = iconColor != 0 && !dark && this.active ? iconColor : textColor;
            String label = getMessage().getString();
            Component comp = boldLabel || style == Style.ACCENT || style == Style.DANGER ? Ui.bold(label) : Ui.ui(label);
            int tw = label.isEmpty() ? 0 : font().width(comp);
            int is = icon == null ? 0 : (label.isEmpty() ? Math.min(16, h - 8) : Math.min(12, h - 8));
            int gap = icon != null && !label.isEmpty() ? 6 : 0;
            int sx = x + (w - (is + gap + tw)) / 2;
            if (icon != null) icon(g, icon, sx, y + (h - is) / 2, is, ic);
            if (!label.isEmpty()) g.text(font(), comp, sx + is + gap, y + (h - 8) / 2, textColor, false);
        }
    }

    /** An/Aus-Schalter ("Aktiviert" grün / "Deaktiviert" grau) */
    public static class ToggleButton extends Button {
        private final boolean on;
        private final boolean locked;

        public ToggleButton(int x, int y, int w, int h, boolean on, boolean locked, OnPress onPress) {
            super(x, y, w, h, Component.literal(locked ? "Immer an" : on ? "Aktiviert" : "Deaktiviert"), onPress, DEFAULT_NARRATION);
            this.on = on;
            this.locked = locked;
            this.active = !locked;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = this.active && isHoveredOrFocused();
            int bg = locked ? 0xFF101712 : on ? (hover ? 0xFF24733A : GREEN) : (hover ? 0xFF141A16 : 0xFF0F1511);
            DivineRender.roundRect(g, x, y, w, h, 3, bg);
            String label = getMessage().getString();
            int color = locked ? GOLD : on ? GREEN_TEXT : 0xFF8B909A;
            centered(g, label, x + w / 2, y + (h - 8) / 2, color);
        }
    }
}
