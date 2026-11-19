package eu.divinesmp.divinecore.screen;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineRender;
import eu.divinesmp.divinecore.Features;
import eu.divinesmp.divinecore.Features.Category;
import eu.divinesmp.divinecore.Features.Feature;
import eu.divinesmp.divinecore.cosmetics.Cosmetics;
import eu.divinesmp.divinecore.net.DivineNetwork;
import eu.divinesmp.divinecore.screen.Ui.IconButton;
import eu.divinesmp.divinecore.screen.Ui.IconButton.Style;
import eu.divinesmp.divinecore.screen.Ui.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * DeuClient Mod-Menü (Rechts-Shift): Karten-Raster mit allen Client-Funktionen,
 * Kategorien, Suche, Favoriten, An/Aus-Schalter und Einstellungen – plus Tab "Kosmetik"
 * für Capes und Flügel.
 */
public class DivineModMenuScreen extends Screen {
    public enum Tab { MODS, COSMETICS }

    private static Category lastCategory = Category.ALL;
    private static Supplier<PlayerSkin> skinLookup;

    private final Screen parent;
    private Tab tab;
    private Category category = lastCategory;
    private String query = "";
    private int scrollRow;
    private boolean wingsTab;
    private EditBox search;
    private final List<AbstractWidget> dynamic = new ArrayList<>();
    private List<Feature> features = List.of();

    public DivineModMenuScreen(Screen parent) {
        this(parent, Tab.MODS);
    }

    public DivineModMenuScreen(Screen parent, Tab tab) {
        super(Component.literal("DeuClient Mod-Menü"));
        this.parent = parent;
        this.tab = tab;
    }

    /* ================= Layout ================= */

    /** Kompaktes Layout für kleine GUI-Größen (z. B. 1080p mit automatischer GUI-Skalierung) */
    private boolean compact() { return this.height < 330 || this.width < 600; }
    /** Inhalt auf maximal 780 GUI-Pixel Breite begrenzen und mittig halten (kleineres Menue) */
    private int margin() { return Math.max(compact() ? 8 : 16, (this.width - 780) / 2); }
    private int headerH() { return compact() ? 20 : 24; }
    private int tabsY() { return compact() ? 30 : 44; }
    private int tabsH() { return compact() ? 18 : 22; }
    private int panelX() { return margin(); }
    private int panelY() { return tabsY() + tabsH() + (compact() ? 6 : 8); }
    private int panelW() { return this.width - 2 * margin(); }
    private int panelH() { return Math.min(this.height - panelY() - (compact() ? 6 : 12), 392); }
    private int cols() { return panelW() >= 600 ? 4 : panelW() >= 420 ? 3 : panelW() >= 290 && tiny() ? 3 : 2; }
    private int chipH() { return compact() ? 16 : 18; }
    private int gridTop() { return panelY() + (compact() ? 28 : 38); }
    private int gap() { return compact() ? 6 : 8; }
    private int cardW() { return (panelW() - 2 * (compact() ? 8 : 12) - (cols() - 1) * gap()) / cols(); }
    private int cardH() { return tiny() ? 58 : compact() ? 72 : 84; }
    /** sehr kleine GUI (z. B. 720p-Fenster mit GUI-Skalierung 4) */
    private boolean tiny() { return this.height < 220; }
    private int gridX() { return panelX() + (compact() ? 8 : 12); }
    private int visibleRows() { return Math.max(1, (panelY() + panelH() - 6 - gridTop() + gap()) / (cardH() + gap())); }

    @Override
    protected void init() {
        features = Features.all(p -> new DivineModMenuScreen(p, Tab.COSMETICS));
        int W = this.width;

        // Kopfzeile rechts: Schließen
        int hh = headerH();
        addRenderableWidget(new IconButton(W - margin() - hh, compact() ? 5 : 10, hh, hh, Ui.I_CLOSE, Component.empty(), Style.DANGER, 0, b -> onClose()));

        // Tabs
        int tx = margin(), ty = tabsY(), th = tabsH();
        int tw = compact() ? 88 : 108;
        addRenderableWidget(new IconButton(tx, ty, tw, th, Ui.I_GRID, Component.literal("MOD-MENÜ"), b -> switchTab(Tab.MODS)).selected(tab == Tab.MODS).bold());
        tx += tw + 4;
        addRenderableWidget(new IconButton(tx, ty, tw, th, Ui.I_WINGS, Component.literal("KOSMETIK"), b -> switchTab(Tab.COSMETICS)).selected(tab == Tab.COSMETICS).bold());
        tx += tw + 4;
        addRenderableWidget(new IconButton(tx, ty, th, th, Ui.I_GEAR, Component.empty(),
                b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));

        if (tab == Tab.MODS) initMods();
        else initCosmetics();
    }

    private void switchTab(Tab t) {
        this.tab = t;
        this.scrollRow = 0;
        rebuildWidgets();
    }

    /* ================= Tab: Mod-Menü ================= */

    private void initMods() {
        int px = gridX(), py = panelY() + (compact() ? 6 : 10);
        for (Category c : Category.values()) {
            boolean fav = c == Category.FAVORITES;
            String label = fav ? "" : c.label;
            int w = fav ? chipH() + 6 : Ui.width(label) + (compact() ? 14 : 24);
            addRenderableWidget(new IconButton(px, py, w, chipH(), fav ? Ui.I_HEART : null, Component.literal(label), Style.NORMAL, fav ? 0xFFFF6B81 : 0, b -> {
                category = c;
                lastCategory = c;
                scrollRow = 0;
                rebuildWidgets();
            }).selected(c == category));
            px += w + (compact() ? 4 : 6);
        }
        int sw = Math.min(170, panelW() - (px - panelX()) - 24);
        if (sw > 60) {
            if (search == null) {
                search = new EditBox(this.font, 0, 0, sw, 16, Component.literal("Suche"));
                search.setBordered(false);
                search.setHint(Ui.ui("Suchen...").withColor(0xFF6F7682));
                search.setResponder(s -> {
                    query = s.toLowerCase(Locale.ROOT);
                    scrollRow = 0;
                    layoutCards();
                });
            }
            search.setWidth(sw - 18);
            search.setPosition(panelX() + panelW() - sw - 12 + 18, py + (chipH() - 8) / 2);
            addRenderableWidget(search);
        }
        layoutCards();
    }

    private List<Feature> filtered() {
        List<Feature> out = new ArrayList<>();
        for (Feature f : features) {
            if (category == Category.FAVORITES && !f.isFavorite()) continue;
            if (category != Category.ALL && category != Category.FAVORITES && f.category() != category) continue;
            if (!query.isEmpty() && !(f.name() + " " + f.description()).toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(f);
        }
        // Favoriten zuerst
        out.sort((a, b) -> Boolean.compare(b.isFavorite(), a.isFavorite()));
        return out;
    }

    /** Baut nur die Karten-Widgets neu (Suche behält dabei den Fokus) */
    private void layoutCards() {
        for (AbstractWidget w : dynamic) removeWidget(w);
        dynamic.clear();
        List<Feature> list = filtered();
        int cols = cols(), cw = cardW();
        int maxRow = Math.max(0, (list.size() + cols - 1) / cols - visibleRows());
        scrollRow = Math.max(0, Math.min(scrollRow, maxRow));
        int start = scrollRow * cols, end = Math.min(list.size(), start + visibleRows() * cols);
        for (int i = start; i < end; i++) {
            Feature f = list.get(i);
            int idx = i - start;
            int x = gridX() + (idx % cols) * (cw + gap());
            int y = gridTop() + (idx / cols) * (cardH() + gap());
            // Favorit
            dynamic.add(addRenderableWidget(new IconButton(x + cw - 20, y + 4, 16, 14, Ui.I_HEART, Component.empty(), Style.GHOST,
                    f.isFavorite() ? 0xFFFF5C72 : 0xFF3A3F4B, b -> { f.toggleFavorite(); layoutCards(); })));
            // Einstellungen
            boolean hasSettings = !f.settings().isEmpty() || f.customScreen() != null;
            int bh = compact() ? 16 : 18, by = y + cardH() - bh - 5;
            IconButton gear = new IconButton(x + 5, by, bh + 2, bh, Ui.I_GEAR, Component.empty(), Style.NORMAL, Ui.TEXT_DIM, b -> openSettings(f));
            gear.active = hasSettings;
            dynamic.add(addRenderableWidget(gear));
            // An/Aus
            boolean on = !f.hasToggle() || f.isOn().getAsBoolean();
            dynamic.add(addRenderableWidget(new ToggleButton(x + bh + 11, by, cw - bh - 16, bh, on, !f.hasToggle(), b -> {
                f.setOn().accept(!f.isOn().getAsBoolean());
                layoutCards();
            })));
        }
    }

    private void openSettings(Feature f) {
        if (f.settings().isEmpty() && f.customScreen() != null) {
            Screen s = f.customScreen().apply(this);
            if (s != null) this.minecraft.gui.setScreen(s);
        } else {
            this.minecraft.gui.setScreen(new FeatureSettingsScreen(this, f));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.MODS && scrollY != 0) {
            scrollRow += scrollY < 0 ? 1 : -1;
            layoutCards();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /* ================= Tab: Kosmetik ================= */

    private int previewW() { return Math.min(compact() ? 140 : 190, panelW() / 3); }
    private int tileW() { return compact() ? 52 : 64; }
    private int tileH() { return compact() ? 66 : 86; }

    private void initCosmetics() {
        int rx = panelX() + previewW() + (compact() ? 16 : 24), py = panelY() + (compact() ? 6 : 10);
        int tabW = compact() ? 66 : 90;
        addRenderableWidget(new IconButton(rx, py, tabW, chipH(), Ui.I_CAPE, Component.literal("Capes"), b -> { wingsTab = false; rebuildWidgets(); }).selected(!wingsTab).bold());
        addRenderableWidget(new IconButton(rx + tabW + 4, py, tabW, chipH(), Ui.I_WINGS, Component.literal("Flügel"), b -> { wingsTab = true; rebuildWidgets(); }).selected(wingsTab).bold());
        DivineConfig cfg = DivineConfig.get();
        int toggleW = compact() ? 84 : 120;
        int toggleX = Math.max(rx + 2 * tabW + 12, panelX() + panelW() - toggleW - 12);
        addRenderableWidget(new ToggleButton(toggleX, py, toggleW, chipH(), cfg.showCosmetics, false, b -> {
            cfg.showCosmetics = !cfg.showCosmetics;
            DivineConfig.save();
            DivineNetwork.sendHello();
            rebuildWidgets();
        }));

        // Kacheln: "Keine" + alle Capes bzw. Flügel
        List<Cosmetics.Item> items = new ArrayList<>();
        items.add(new Cosmetics.Item("", "Keine"));
        if (!wingsTab && eu.divinesmp.divinecore.cosmetics.CustomCapes.self() != null) items.add(new Cosmetics.Item("custom", "Eigenes"));
        items.addAll(wingsTab ? Cosmetics.WINGS : Cosmetics.CAPES);
        int tileW = tileW(), tileH = tileH(), gap = compact() ? 6 : 8;
        int areaW = panelX() + panelW() - 12 - rx;
        int perRow = Math.max(1, (areaW + gap) / (tileW + gap));
        for (int i = 0; i < items.size(); i++) {
            Cosmetics.Item it = items.get(i);
            int x = rx + (i % perRow) * (tileW + gap);
            int y = py + chipH() + (compact() ? 8 : 12) + (i / perRow) * (tileH + gap);
            if (y + tileH > panelY() + panelH() - 8) break;
            addRenderableWidget(new IconButton(x, y, tileW, tileH, null, Component.empty(), Style.GHOST, 0, b -> {
                if (wingsTab) cfg.wings = it.id();
                else cfg.cape = it.id();
                DivineConfig.save();
                DivineNetwork.sendHello();
            }));
        }
    }

    private void drawCosmetics(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int px = panelX(), py = panelY();
        // Vorschau links
        int pw = previewW(), ph = panelH() - (compact() ? 12 : 20);
        int vx = px + (compact() ? 8 : 12), vy = py + (compact() ? 6 : 10);
        DivineRender.roundRect(g, vx, vy, pw, ph, 5, Ui.CARD);
        DivineRender.roundGradient(g, vx, vy, pw, ph / 2, 5, 0x2E22C55E, 0x00000000);
        if (this.minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, vx, vy + 10, vx + pw, vy + ph - 40, Math.min(70, ph / 3), 0.0625f, mouseX, mouseY, this.minecraft.player);
        } else {
            DivineConfig cfg = DivineConfig.get();
            int cx = vx + pw / 2;
            int avail = ph - 64; // Platz über den Texten
            int wingS = Math.min(pw / 2 - 4, avail / 2);
            int capeH = Math.min(avail - wingS - 6, 96), capeW = capeH * 10 / 16;
            net.minecraft.resources.Identifier previewCape = "custom".equals(cfg.cape) ? eu.divinesmp.divinecore.cosmetics.CustomCapes.self() : cfg.cape.isEmpty() ? null : Cosmetics.capeTexture(cfg.cape);
            if (previewCape != null && capeH > 16) {
                g.blit(RenderPipelines.GUI_TEXTURED, previewCape, cx - capeW / 2, vy + 8, 1f, 1f, capeW, capeH, 10, 16, 64, 32);
            }
            if (!cfg.wings.isEmpty()) {
                int wy = vy + 8 + Math.max(capeH, 0) + 6;
                Ui.wingPair(g, Cosmetics.wingsTexture(cfg.wings), cx, wy, wingS);
            }
            Ui.centered(g, "3D-Vorschau im Spiel", cx, vy + ph - 44, Ui.TEXT_DIM);
        }
        Ui.centered(g, "Für alle DeuClient-Spieler", vx + pw / 2, vy + ph - 30, Ui.GOLD);
        Ui.centered(g, "sichtbar · " + Cosmetics.remoteCount() + " online", vx + pw / 2, vy + ph - 18, Ui.TEXT_DIM);

        // Kacheln
        DivineConfig cfg = DivineConfig.get();
        String selected = wingsTab ? cfg.wings : cfg.cape;
        int rx = px + previewW() + (compact() ? 16 : 24);
        List<Cosmetics.Item> items = new ArrayList<>();
        items.add(new Cosmetics.Item("", "Keine"));
        if (!wingsTab && eu.divinesmp.divinecore.cosmetics.CustomCapes.self() != null) items.add(new Cosmetics.Item("custom", "Eigenes"));
        items.addAll(wingsTab ? Cosmetics.WINGS : Cosmetics.CAPES);
        int tileW = tileW(), tileH = tileH(), gap = compact() ? 6 : 8;
        int areaW = px + panelW() - 12 - rx;
        int perRow = Math.max(1, (areaW + gap) / (tileW + gap));
        for (int i = 0; i < items.size(); i++) {
            Cosmetics.Item it = items.get(i);
            int x = rx + (i % perRow) * (tileW + gap);
            int y = py + (compact() ? 6 : 10) + chipH() + (compact() ? 8 : 12) + (i / perRow) * (tileH + gap);
            if (y + tileH > py + panelH() - 8) break;
            boolean sel = it.id().equals(selected);
            boolean hover = mouseX >= x && mouseX < x + tileW && mouseY >= y && mouseY < y + tileH;
            if (sel) DivineRender.roundRect(g, x - 1, y - 1, tileW + 2, tileH + 2, 6, Ui.GOLD);
            DivineRender.roundRect(g, x, y, tileW, tileH, 5, hover ? Ui.CARD_HOVER : Ui.CARD);
            if (it.id().isEmpty()) {
                Ui.iconCentered(g, Ui.I_CLOSE, x + tileW / 2, y + (tileH - 14) / 2, compact() ? 16 : 22, 0xFF4A505C);
            } else if (wingsTab) {
                int s = (tileW - 6) / 2;
                int wy = y + (tileH - 14 - s) / 2;
                Ui.wingPair(g, Cosmetics.wingsTexture(it.id()), x + tileW / 2, wy, s);
            } else {
                int ch = tileH - 22, cw2 = ch * 10 / 16;
                net.minecraft.resources.Identifier capeTex = "custom".equals(it.id()) ? eu.divinesmp.divinecore.cosmetics.CustomCapes.self() : Cosmetics.capeTexture(it.id());
                if (capeTex != null) g.blit(RenderPipelines.GUI_TEXTURED, capeTex, x + (tileW - cw2) / 2, y + 5, 1f, 1f, cw2, ch, 10, 16, 64, 32);
            }
            String name = Ui.ellipsize(it.name(), tileW - 6);
            Ui.centered(g, name, x + tileW / 2, y + tileH - 12, sel ? Ui.GOLD : Ui.TEXT);
        }
    }

    /* ================= Zeichnen ================= */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta); // Panorama/Divine-Hintergrund bzw. Spielwelt
        g.fillGradient(0, 0, this.width, this.height, 0x80050806, 0xA0050806);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int W = this.width;
        // ---- Kopf: Logo + Titel + Datum
        boolean c = compact();
        int top = c ? 5 : 10, hh = headerH();
        String title = tab == Tab.MODS ? "MOD-MENÜ" : "KOSMETIK";
        float ts = c ? 1.25f : 1.6f;
        DivineRender.drawEmblem(g, margin(), top - (c ? 1 : 2), c ? 22 : 28, 255);
        Ui.title(g, title, margin() + (c ? 27 : 34), top + (c ? 5 : 6), ts, Ui.TEXT);
        String name = this.minecraft.getUser().getName().toUpperCase();
        int chipW = Ui.boldWidth(name) + (c ? 28 : 36);
        int chipX = W - margin() - hh - 6 - chipW;
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern(c ? "HH:mm" : "dd.MM.yyyy   HH:mm"));
        int dx = margin() + (c ? 27 : 34) + (int) (Ui.boldWidth(title) * ts) + 12;
        int dw = Ui.boldWidth(date) + 28;
        if (dx + dw < chipX - 6) {
            DivineRender.roundRect(g, dx, top, dw, hh, 4, Ui.FIELD);
            Ui.icon(g, Ui.I_MOON, dx + 7, top + (hh - 10) / 2, 10, Ui.TEXT);
            Ui.boldText(g, date, dx + 21, top + (hh - 8) / 2, Ui.TEXT);
        }

        // ---- Spieler-Chip oben rechts
        DivineRender.roundRect(g, chipX, top, chipW, hh, 4, Ui.FIELD);
        int face = c ? 12 : 16;
        PlayerFaceExtractor.extractRenderState(g, skin(), chipX + 5, top + (hh - face) / 2, face);
        Ui.boldText(g, name, chipX + face + 11, top + (hh - 8) / 2, Ui.TEXT);

        // ---- Panel
        DivineRender.roundRect(g, panelX(), panelY(), panelW(), panelH(), 6, Ui.BG);

        if (tab == Tab.MODS) {
            if (search != null && this.children().contains(search)) {
                int fx = search.getX() - 18, fy = panelY() + (compact() ? 6 : 10);
                DivineRender.roundRect(g, fx, fy, search.getWidth() + 24, chipH(), 4, Ui.FIELD);
                Ui.icon(g, Ui.I_SEARCH, fx + 6, fy + (chipH() - 10) / 2, 10, Ui.TEXT_DIM);
            }
            drawCards(g, mouseX, mouseY);
        } else {
            drawCosmetics(g, mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawCards(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Feature> list = filtered();
        int cols = cols(), cw = cardW();
        int start = scrollRow * cols, end = Math.min(list.size(), start + visibleRows() * cols);
        if (list.isEmpty()) {
            Ui.centered(g, "Keine Funktionen gefunden.", panelX() + panelW() / 2, gridTop() + 30, Ui.TEXT_DIM);
        }
        for (int i = start; i < end; i++) {
            Feature f = list.get(i);
            int idx = i - start;
            int x = gridX() + (idx % cols) * (cw + gap());
            int y = gridTop() + (idx / cols) * (cardH() + gap());
            boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + cardH();
            boolean on = !f.hasToggle() || f.isOn().getAsBoolean();
            DivineRender.roundRect(g, x, y, cw, cardH(), 5, hover ? Ui.CARD_HOVER : Ui.CARD);
            if (hover) DivineRender.roundRect(g, x + 1, y, cw - 2, 1, 0, 0x55FFFFFF);
            if (f.isNew()) {
                int bw = Ui.boldWidth("NEU") + 8;
                DivineRender.roundRect(g, x - 2, y - 4, bw, 10, 2, 0xFF1F8A3E);
                Ui.boldText(g, "NEU", x + 2, y - 3, 0xFFFFFFFF);
            }
            Ui.boldText(g, Ui.ellipsize(f.name(), cw - 30), x + 8, y + (compact() ? 7 : 9), on ? Ui.TEXT : Ui.TEXT_DIM);
            int is = tiny() ? 18 : compact() ? 24 : 34;
            Ui.iconCentered(g, f.icon(), x + cw / 2, y + (tiny() ? 28 : compact() ? 35 : 46), is, on ? 0xFFFFFFFF : 0xFF4A505C);
        }
        // Scroll-Anzeige
        int rows = (list.size() + cols - 1) / cols;
        if (rows > visibleRows()) {
            int trackH = panelY() + panelH() - 8 - gridTop();
            int barH = Math.max(20, trackH * visibleRows() / rows);
            int barY = gridTop() + (trackH - barH) * scrollRow / Math.max(1, rows - visibleRows());
            DivineRender.roundRect(g, panelX() + panelW() - 6, barY, 3, barH, 1, 0x8822C55E);
        }
        // Tooltip: Beschreibung der Karte unter der Maus
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int x = gridX() + (idx % cols) * (cw + gap());
            int y = gridTop() + (idx / cols) * (cardH() + gap());
            if (mouseX >= x && mouseX < x + cw && mouseY >= y + 16 && mouseY < y + cardH() - 24) {
                g.setTooltipForNextFrame(this.font, this.font.split(Ui.ui(list.get(i).description()), 220), mouseX, mouseY);
            }
        }
    }

    private PlayerSkin skin() {
        if (skinLookup == null) skinLookup = Minecraft.getInstance().getSkinManager().createLookup(Minecraft.getInstance().getGameProfile(), false);
        return skinLookup.get();
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
