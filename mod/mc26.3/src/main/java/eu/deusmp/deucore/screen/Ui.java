package eu.deusmp.deucore.screen;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.DeuCore;
import eu.deusmp.deucore.Tr;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.cosmetics.Cosmetics;
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
 * UI-Bausteine des DeuClients im cleanen Client-Look:
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
    public static final int GOLD = 0xFF2EFF7A;
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
    public static final String I_MINUS = "minus", I_PLUS = "plus", I_CHECK = "check", I_LOCK = "lock", I_UNLOCK = "unlock", I_HEART_FILLED = "heart_filled", I_PLAY = "play";

    /* ================= Schrift ================= */

    /*
     * Die Schrift-Textur wird ohne Filter gezeichnet: nur wenn sie mit genau der GUI-Skalierung aufgeloest ist
     * (oversample == Skalierung), liegt jedes Textur-Pixel auf einem Bildschirm-Pixel und die Schrift ist scharf.
     * Darum gibt es je GUI-Skalierung eine eigene Schrift (ui_2, ui_bold_3 ...).
     */
    private static final int MAX_FONT_SCALE = 6;
    private static final FontDescription[] UI_FONTS = new FontDescription[MAX_FONT_SCALE + 1];
    private static final FontDescription[] BOLD_FONTS = new FontDescription[MAX_FONT_SCALE + 1];
    private static final Style[] UI_STYLES = new Style[MAX_FONT_SCALE + 1];
    private static final Style[] BOLD_STYLES = new Style[MAX_FONT_SCALE + 1];

    private static int fontScale() {
        return Math.max(1, Math.min(MAX_FONT_SCALE, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale())));
    }

    /** Schrift passend zur aktuellen GUI-Skalierung */
    public static FontDescription uiFont() {
        int s = fontScale();
        if (UI_FONTS[s] == null) UI_FONTS[s] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "ui_" + s));
        return UI_FONTS[s];
    }

    private static Style uiStyle() {
        int s = fontScale();
        if (UI_STYLES[s] == null) UI_STYLES[s] = Style.EMPTY.withFont(uiFont());
        return UI_STYLES[s];
    }

    private static Style boldStyle() {
        int s = fontScale();
        if (BOLD_STYLES[s] == null) {
            BOLD_FONTS[s] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "ui_bold_" + s));
            BOLD_STYLES[s] = Style.EMPTY.withFont(BOLD_FONTS[s]);
        }
        return BOLD_STYLES[s];
    }

    public static MutableComponent ui(String s) {
        return Component.literal(Tr.t(s)).setStyle(uiStyle());
    }

    public static MutableComponent bold(String s) {
        return Component.literal(Tr.t(s)).setStyle(boldStyle());
    }

    private static java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> selfSkin;
    private static volatile net.minecraft.world.entity.player.PlayerSkin urlSkin;
    private static boolean urlSkinStarted;

    /** Laedt den Skin des Spielers von der vom Launcher uebergebenen URL (textures.minecraft.net) */
    private static void loadUrlSkin(String url) {
        urlSkinStarted = true;
        Thread t = new Thread(() -> {
            try (java.io.InputStream in = java.net.URI.create(url).toURL().openStream()) {
                byte[] bytes = in.readAllBytes();
                Minecraft.getInstance().execute(() -> {
                    try {
                        Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/self_skin");
                        Minecraft.getInstance().getTextureManager().register(id,
                                new net.minecraft.client.renderer.texture.DynamicTexture(id::toString, NativeImage.read(bytes)));
                        net.minecraft.core.ClientAsset.Texture body = new net.minecraft.core.ClientAsset.ResourceTexture(id, id);
                        urlSkin = new net.minecraft.world.entity.player.PlayerSkin(body, null, null,
                                net.minecraft.world.entity.player.PlayerModelType.WIDE, true);
                    } catch (Exception e) {
                        DeuCore.LOGGER.warn("Skin konnte nicht gelesen werden: {}", e.toString());
                    }
                });
            } catch (Exception e) {
                DeuCore.LOGGER.warn("Skin konnte nicht geladen werden: {}", e.toString());
            }
        }, "DeuClient-Skin");
        t.setDaemon(true);
        t.start();
    }

    /** Skin des angemeldeten Spielers: in der Welt der echte, sonst der vom Launcher gelieferte */
    public static net.minecraft.world.entity.player.PlayerSkin selfSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) return mc.player.getSkin();
        String url = System.getProperty("deuclient.skin");
        if (url != null && !url.isEmpty()) {
            if (!urlSkinStarted) loadUrlSkin(url);
            if (urlSkin != null) return urlSkin;
        }
        if (selfSkin == null) {
            selfSkin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
        }
        return selfSkin.get();
    }

    /** Hilfe-Text im DeuClient-Stil (dunkle abgerundete Box mit gruenem Rand) statt des violetten Vanilla-Tooltips */
    public static void tooltip(GuiGraphicsExtractor g, int mx, int my, String text, int maxW, int screenW, int screenH) {
        Font f = font();
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = f.split(ui(text), maxW);
        int tw = 0;
        for (var l : lines) tw = Math.max(tw, f.width(l));
        int w = tw + 16, h = lines.size() * 11 + 9;
        int x = mx + 12, y = my + 14;
        if (x + w > screenW - 4) x = Math.max(4, mx - w - 8);
        if (y + h > screenH - 4) y = Math.max(4, screenH - 4 - h);
        DeuRender.roundRect(g, x - 1, y - 1, w + 2, h + 2, 5, 0x802EFF7A);
        DeuRender.roundRect(g, x, y, w, h, 4, 0xF70A0F0C);
        int ty = y + 5;
        for (var l : lines) {
            g.text(f, l, x + 8, ty, TEXT, false);
            ty += 11;
        }
    }

    /** Panel im DeuClient-Stil: dunkel, abgerundet, duenner gruener Rand */
    public static void themedPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DeuRender.roundRect(g, x, y, w, h, 6, 0xF20A0F0C);
        DeuRender.roundOutline(g, x, y, w, h, 6, 0x802EFF7A);
    }

    /** Runder gruener Start-Button mit Play-Icon ueber dem Welt-/Server-Icon (w x h = Icon-Flaeche) */
    public static void joinButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hi) {
        DeuRender.roundRect(g, x, y, w, h, 6, 0xB00A1A10);
        int bs = 22, bx = x + (w - bs) / 2, by = y + (h - bs) / 2;
        DeuRender.roundRect(g, bx, by, bs, bs, 7, hi ? 0xFF2EFF7A : 0xFF1FD35F);
        iconCentered(g, I_PLAY, x + w / 2, y + h / 2, 12, 0xFF03140A);
    }

    /** Tab-Knopf im DeuClient-Stil (selected = gruener Rand, hover = heller) */
    public static void themedTab(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, boolean hover) {
        DeuRender.roundRect(g, x + 1, y, w - 2, h, 4, selected ? 0xF2112019 : hover ? 0xF2141A16 : 0xE60A0F0C);
        DeuRender.roundOutline(g, x + 1, y, w - 2, h, 4, selected ? 0xB02EFF7A : 0x30FFFFFF);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int width(String s) {
        return font().width(ui(s));
    }

    /** Exakte (Gleitkomma-)Breite - fuer genaues Zentrieren und Rechtsbuendigkeit */
    public static float widthF(String s) {
        return font().getSplitter().stringWidth(ui(s));
    }

    /**
     * Text an einer Gleitkomma-Position, auf Bildschirm-Pixel gerastert (zentriert/rechtsbuendig bleibt exakt,
     * die Schrift bleibt dabei scharf, weil sie nie zwischen zwei Pixeln liegt).
     */
    public static void textAt(GuiGraphicsExtractor g, String s, float x, float y, int color) {
        double k = DeuRender.totalScale(g);
        double sx = Math.round(x * k) / k, sy = Math.round(y * k) / k;
        int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
        float fx = (float) (sx - ix), fy = (float) (sy - iy);
        if (fx == 0f && fy == 0f) {
            text(g, s, ix, iy, color);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(fx, fy);
        text(g, s, ix, iy, color);
        g.pose().popMatrix();
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
        s = Tr.t(s);
        if (width(s) <= maxW) return s;
        while (s.length() > 1 && width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /* ================= Icons ================= */

    public static void icon(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, DeuRender.icon(name), x, y, 0f, 0f, size, size, 64, 64, 64, 64, color);
    }

    public static void iconCentered(GuiGraphicsExtractor g, String name, int cx, int cy, int size, int color) {
        icon(g, name, cx - size / 2, cy - size / 2, size, color);
    }

    /** Scharfes Icon aus icons_hd: Textur exakt in Bildschirm-Pixeln (1 Textur-Pixel = 1 Bildschirm-Pixel) */
    public static void iconSharp(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
        int px = Math.max(2, (int) Math.round(size * DeuRender.totalScale(g)));
        Identifier tex = DeuRender.iconPx(name, px);
        if (tex == null) return;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, size, size, px, px, px, px, color);
    }

    /** Vorderseite eines Capes (eingebaut = weiche Vorschau, "custom" = eigenes Cape im 64x32-Format) */
    public static void capeFront(GuiGraphicsExtractor g, String id, int x, int y, int w, int h) {
        if ("custom".equals(id)) {
            Identifier t = eu.deusmp.deucore.cosmetics.CustomCapes.self();
            if (t != null) g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 1f, 1f, w, h, 10, 16, 64, 32);
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, Cosmetics.capeUi(id), x, y, 0f, 0f, w, h, 10, 16, 10, 16);
    }

    /** Zeichnet ein Flügelpaar (Textur + gespiegelte Textur), Wurzeln in der Mitte bei cx */
    public static void wingPair(GuiGraphicsExtractor g, Identifier tex, int cx, int y, int size) {
        // rechts: normal; links: per UV gespiegelt (negative Breite) - GUI verwirft gespiegelte Flächen
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
                    DeuRender.roundGradient(g, x, y, w, h, r, hover ? 0xFF6BFF9E : 0xFF2EFF7A, hover ? 0xFF1FD35F : 0xFF17A84A);
                    dark = true;
                }
                case DANGER -> DeuRender.roundRect(g, x, y, w, h, r, hover ? 0x99B4233A : 0x55801628);
                case GHOST -> { if (hover) DeuRender.roundRect(g, x, y, w, h, r, 0x18FFFFFF); }
                default -> {
                    if (selected) {
                        DeuRender.roundGradient(g, x, y, w, h, r, 0xFF2EFF7A, 0xFF17A84A);
                        dark = true;
                    } else {
                        if (hover) DeuRender.roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, 0xFF2EFF7A);
                        DeuRender.roundRect(g, x, y, w, h, r, hover ? FIELD_HOVER : FIELD);
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
            DeuRender.roundRect(g, x, y, w, h, 3, bg);
            String label = getMessage().getString();
            int color = locked ? GOLD : on ? GREEN_TEXT : 0xFF8B909A;
            centered(g, label, x + w / 2, y + (h - 8) / 2, color);
        }
    }
}
