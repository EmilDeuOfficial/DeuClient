package eu.deusmp.deucore.screen;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Features;
import eu.deusmp.deucore.Tr;
import eu.deusmp.deucore.Features.Category;
import eu.deusmp.deucore.Features.Feature;
import eu.deusmp.deucore.cosmetics.Cosmetics;
import eu.deusmp.deucore.cosmetics.CustomCapes;
import eu.deusmp.deucore.net.DeuNetwork;
import eu.deusmp.deucore.screen.Ui.IconButton;
import eu.deusmp.deucore.screen.Ui.IconButton.Style;
import eu.deusmp.deucore.screen.Ui.ToggleButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DeuClient Mod-Menü (Rechts-Shift): Kopfzeile mit Titel, Uhr und Profil, darunter die Reiter
 * (Mod-Menü, Capes, Flügel, HUD, Themefarbe, Optionen) und eine ruhige Fläche mit Karten bzw. Kosmetik.
 */
public class DeuModMenuScreen extends Screen {
    public enum Tab { MODS, COSMETICS }

    private static Category lastCategory = Category.ALL;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("dd.MM.yyyy   HH:mm");

    private String tipText; // Hilfe-Text unter der Maus
    private final Screen parent;
    private Tab tab;
    private Category category = lastCategory;
    private String query = "";
    private int scrollRow;
    private boolean wingsTab;
    private int cosmeticScroll; // erste sichtbare Zeile der Kollektionen
    private EditBox search;
    private final List<AbstractWidget> dynamic = new ArrayList<>();
    private final List<Tip> tabTips = new ArrayList<>();
    private List<Feature> features = List.of();
    /** Drehung der Spielervorschau: 0 = von hinten */
    private float previewYaw = 25f;
    private boolean turning;

    private record Tip(int x, int y, int w, int h, String text) {}

    public DeuModMenuScreen(Screen parent) {
        this(parent, Tab.MODS);
    }

    public DeuModMenuScreen(Screen parent, Tab tab) {
        super(Component.literal("DeuClient Mod-Menü"));
        this.parent = parent;
        this.tab = tab;
    }

    /** Direkt im Fluegel-Tab oeffnen */
    public DeuModMenuScreen wings() {
        tab = Tab.COSMETICS;
        wingsTab = true;
        return this;
    }

    /** Nur fuer Testbilder (DemoMode): Spielervorschau gedreht oeffnen */
    public DeuModMenuScreen turned(float yaw) {
        previewYaw = yaw;
        return this;
    }

    /* ================= Layout ================= */

    /** Kompaktes Layout für kleine GUI-Größen (z. B. 1080p mit automatischer GUI-Skalierung) */
    private boolean compact() { return this.height < 330 || this.width < 600; }
    private int headerH() { return compact() ? 18 : 24; }
    private int tabH() { return compact() ? 18 : 24; }
    private int spacing() { return compact() ? 4 : 8; }
    private int panelW() { return Math.min(this.width - (compact() ? 20 : 48), 620); }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelH() { return Math.min(this.height - headerH() - tabH() - 2 * spacing() - (compact() ? 12 : 28), 340); }
    private int topY() { return Math.max(4, (this.height - headerH() - tabH() - 2 * spacing() - panelH()) / 2); }
    private int tabsY() { return topY() + headerH() + spacing(); }
    private int panelY() { return tabsY() + tabH() + spacing(); }
    private int chipH() { return compact() ? 16 : 19; }
    private int gap() { return compact() ? 5 : 8; }
    /** Innenabstand des Panels (oben, unten und an den Seiten gleich) */
    private int pad() { return compact() ? 6 : 10; }
    private int innerX() { return panelX() + pad(); }
    private int innerW() { return panelW() - 2 * pad(); }
    private int gridTop() { return panelY() + pad() + chipH() + gap(); }
    private int gridW() { return innerW() - 5; } // rechts Platz fuer die Scroll-Anzeige
    private int cols() { int w = gridW(); return w >= 520 ? 5 : w >= 370 ? 4 : w >= 260 ? 3 : 2; }
    private int cardW() { return (gridW() - (cols() - 1) * gap()) / cols(); }
    private int gridH() { return panelY() + panelH() - pad() - gridTop(); }
    private int baseCardH() { return compact() ? 70 : 96; }
    /** Anzahl sichtbarer Reihen: moeglichst viele, solange die Karten nicht zu klein werden */
    private int rows() {
        int avail = gridH(), min = compact() ? 62 : 84;
        int r = Math.max(1, (avail + gap()) / (baseCardH() + gap()));
        if ((avail - r * gap()) / (r + 1) >= min) r++;
        return r;
    }
    private int cardH() { return Math.max(44, Math.min(baseCardH() + 10, (gridH() - (rows() - 1) * gap()) / rows())); }
    private int buttonH() { return compact() ? 14 : 17; }

    @Override
    protected void init() {
        features = Features.all(p -> new DeuModMenuScreen(p, Tab.COSMETICS));
        tabTips.clear();
        initTabs();
        if (tab == Tab.MODS) initMods();
        else initCosmetics();
    }

    private void initTabs() {
        int x = panelX(), y = tabsY(), h = tabH();
        x = tabButton(x, y, h, Ui.I_GRID, "Mod-Menü", tab == Tab.MODS, () -> switchTab(Tab.MODS, false));
        x = tabButton(x, y, h, Ui.I_CAPE, "Capes", tab == Tab.COSMETICS && !wingsTab, () -> switchTab(Tab.COSMETICS, false));
        x = tabButton(x, y, h, Ui.I_WINGS, "Flügel", tab == Tab.COSMETICS && wingsTab, () -> switchTab(Tab.COSMETICS, true));
        x = tabButton(x, y, h, Ui.I_HUD, "HUD bearbeiten", false, () -> this.minecraft.gui.setScreen(new HudMoveScreen(this)));
        x = tabButton(x, y, h, Ui.I_PAINT_BRUSH, "Themefarbe", false, () -> features.stream().filter(f -> f.id().equals("theme")).findFirst()
                .ifPresent(f -> this.minecraft.gui.setScreen(new FeatureSettingsScreen(this, f))));
        tabButton(x, y, h, Ui.I_GEAR, "Optionen", false, () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options)));
        addRenderableWidget(new IconButton(panelX() + panelW() - h, y, h, h, Ui.I_CLOSE, Component.empty(), Style.DANGER, 0, b -> onClose()));
    }

    /** Aktiver Reiter breit mit Beschriftung, die anderen als quadratisches Icon mit Hilfe-Text */
    private int tabButton(int x, int y, int h, String icon, String label, boolean active, Runnable action) {
        int w = active ? Ui.boldWidth(label) + 34 : h + (compact() ? 4 : 8);
        addRenderableWidget(new IconButton(x, y, w, h, icon, active ? Component.literal(label) : Component.empty(), b -> action.run()).selected(active).bold());
        if (!active) tabTips.add(new Tip(x, y, w, h, label));
        return x + w + 4;
    }

    private void switchTab(Tab t, boolean wings) {
        this.tab = t;
        this.wingsTab = wings;
        this.scrollRow = 0;
        this.cosmeticScroll = 0;
        rebuildWidgets();
    }

    /* ================= Tab: Mod-Menü ================= */

    private void initMods() {
        // Kategorien als Knoepfe oben links
        int x = innerX(), y = panelY() + pad(), h = chipH();
        for (Category c : Category.values()) {
            if (c == Category.COSMETICS || c == Category.FAVORITES) continue; // Kosmetik hat eigene Reiter, Favoriten das Herz
            if (c != Category.ALL && features.stream().noneMatch(f -> f.category() == c)) continue;
            int w = Ui.width(c.label) + (compact() ? 14 : 20);
            addRenderableWidget(new IconButton(x, y, w, h, null, Component.literal(c.label), b -> setCategory(c)).selected(c == category));
            x += w + 3;
        }
        int heartX = innerX() + innerW() - h;
        boolean fav = category == Category.FAVORITES;
        addRenderableWidget(new IconButton(heartX, y, h, h, fav ? Ui.I_HEART_FILLED : Ui.I_HEART, Component.empty(),
                b -> setCategory(category == Category.FAVORITES ? Category.ALL : Category.FAVORITES)).selected(fav));
        tabTips.add(new Tip(heartX, y, h, h, "Favoriten"));
        // Suche zwischen Kategorien und Herz
        if (search == null) {
            search = new EditBox(this.font, 0, 0, 100, 16, Component.literal("Suche"));
            search.setBordered(false);
            search.setHint(Ui.ui("Suchen...").withColor(0xFF6F7682));
            search.setResponder(s -> {
                query = s.toLowerCase(Locale.ROOT);
                scrollRow = 0;
                layoutCards();
            });
        }
        search.setWidth(Math.max(20, searchW() - 22));
        search.setPosition(searchX() + 18, y + (h - 8) / 2);
        addRenderableWidget(search);
        layoutCards();
    }

    private int searchX() {
        int x = innerX();
        for (Category c : Category.values()) {
            if (c == Category.COSMETICS || c == Category.FAVORITES) continue;
            if (c != Category.ALL && features.stream().noneMatch(f -> f.category() == c)) continue;
            x += Ui.width(c.label) + (compact() ? 14 : 20) + 3;
        }
        return x + 3;
    }

    private int searchW() { return innerX() + innerW() - chipH() - 4 - searchX(); }

    private void setCategory(Category c) {
        category = c;
        lastCategory = c;
        scrollRow = 0;
        rebuildWidgets();
    }

    private List<Feature> filtered() {
        List<Feature> out = new ArrayList<>();
        for (Feature f : features) {
            if (category == Category.FAVORITES && !f.isFavorite()) continue;
            if (category != Category.ALL && category != Category.FAVORITES && f.category() != category) continue;
            if (!query.isEmpty() && !(Tr.t(f.name()) + " " + Tr.t(f.description())).toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(f);
        }
        // Favoriten zuerst
        out.sort((a, b) -> Boolean.compare(b.isFavorite(), a.isFavorite()));
        return out;
    }

    private static boolean hasSettings(Feature f) {
        return !f.settings().isEmpty() || f.customScreen() != null;
    }

    /** Baut nur die Karten-Widgets neu (Suche behält dabei den Fokus) */
    private void layoutCards() {
        for (AbstractWidget w : dynamic) removeWidget(w);
        dynamic.clear();
        List<Feature> list = filtered();
        int cols = cols(), cw = cardW(), bh = buttonH(), in = compact() ? 5 : 7;
        int maxRow = Math.max(0, (list.size() + cols - 1) / cols - rows());
        scrollRow = Math.max(0, Math.min(scrollRow, maxRow));
        int start = scrollRow * cols, end = Math.min(list.size(), start + rows() * cols);
        for (int i = start; i < end; i++) {
            Feature f = list.get(i);
            int idx = i - start;
            int x = innerX() + (idx % cols) * (cw + gap());
            int y = gridTop() + (idx / cols) * (cardH() + gap());
            // Herz oben rechts; unten Zahnrad und An/Aus
            dynamic.add(addRenderableWidget(new IconButton(x + cw - 17, y + 3, 14, 14, f.isFavorite() ? Ui.I_HEART_FILLED : Ui.I_HEART, Component.empty(), Style.GHOST,
                    f.isFavorite() ? Ui.GOLD : 0xFF52565E, b -> { f.toggleFavorite(); layoutCards(); })));
            int bx = x + in, by = y + cardH() - bh - in, bw = cw - 2 * in;
            if (hasSettings(f)) {
                dynamic.add(addRenderableWidget(new IconButton(bx, by, bh + 2, bh, Ui.I_GEAR, Component.empty(), b -> openSettings(f))));
                bx += bh + 5;
                bw -= bh + 5;
            }
            boolean on = !f.hasToggle() || f.isOn().getAsBoolean();
            dynamic.add(addRenderableWidget(new ToggleButton(bx, by, bw, bh, on, !f.hasToggle(), b -> {
                f.setOn().accept(!f.isOn().getAsBoolean());
                layoutCards();
            })));
        }
    }

    private void openSettings(Feature f) {
        if (f.settings().isEmpty() && f.customScreen() != null) {
            if (f.hasToggle() && !f.isOn().getAsBoolean()) return; // ausgeschaltetes Modul: Menue gesperrt
            Screen s = f.customScreen().apply(this);
            if (s != null) this.minecraft.gui.setScreen(s);
        } else {
            this.minecraft.gui.setScreen(new FeatureSettingsScreen(this, f));
        }
    }

    private void drawCards(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Suchfeld
        int fy = panelY() + pad(), h = chipH();
        DeuRender.roundRect(g, searchX(), fy, searchW(), h, 4, Ui.FIELD);
        Ui.icon(g, Ui.I_SEARCH, searchX() + 6, fy + (h - 9) / 2, 9, Ui.TEXT_DIM);

        List<Feature> list = filtered();
        int cols = cols(), cw = cardW(), bh = buttonH(), in = compact() ? 5 : 7;
        int start = scrollRow * cols, end = Math.min(list.size(), start + rows() * cols);
        if (list.isEmpty()) Ui.centered(g, "Keine Funktionen gefunden.", this.width / 2, gridTop() + 30, Ui.TEXT_DIM);
        for (int i = start; i < end; i++) {
            Feature f = list.get(i);
            int idx = i - start;
            int x = innerX() + (idx % cols) * (cw + gap());
            int y = gridTop() + (idx / cols) * (cardH() + gap());
            boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + cardH();
            boolean on = !f.hasToggle() || f.isOn().getAsBoolean();
            DeuRender.roundRect(g, x, y, cw, cardH(), 5, hover ? Ui.CARD_HOVER : Ui.CARD);
            // Name oben links, Icon in der Mitte
            Ui.headingText(g, ellipsizeHeading(f.name(), cw - in - 22), x + in + 1, y + 5, on ? 0xFFFFFFFF : 0xFFB4B9C2);
            int areaTop = y + 17, areaBottom = y + cardH() - bh - in - 2;
            int is = Math.max(10, Math.min(compact() ? 22 : 30, areaBottom - areaTop - 4));
            Ui.iconCentered(g, f.icon(), x + cw / 2, (areaTop + areaBottom) / 2, is, on ? 0xFFFFFFFF : 0xFF62666E);
            if (mouseX >= x && mouseX < x + cw && mouseY >= areaTop && mouseY < areaBottom) tipText = f.description();
        }
        // Scroll-Anzeige
        int rows = (list.size() + cols - 1) / cols;
        if (rows > rows()) {
            int trackH = gridH();
            int barH = Math.max(16, trackH * rows() / rows);
            int barY = gridTop() + (trackH - barH) * scrollRow / Math.max(1, rows - rows());
            DeuRender.roundRect(g, panelX() + panelW() - pad() / 2 - 2, barY, 2, barH, 1, 0x55FFFFFF);
        }
    }

    private static String ellipsizeHeading(String s, int maxW) {
        s = Tr.t(s);
        if (Ui.headingWidth(s) <= maxW) return s;
        while (s.length() > 1 && Ui.headingWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /* ================= Tab: Kosmetik ================= */

    private int previewW() { return Math.min(compact() ? 140 : 220, panelW() * 2 / 5); }
    private int listW() { return innerW() - previewW() - pad(); }
    private int previewX() { return innerX() + listW() + pad(); }
    /** Untere Grenze der Kachel-Flaeche; im Fluegel-Tab bleibt unten Platz fuer die Groessen-Einstellung */
    private int tileBottom() { return panelY() + panelH() - pad() - (wingsTab ? chipH() + gap() : 0); }
    private int sizeRowY() { return panelY() + panelH() - pad() - chipH(); }
    private int sizeButtonsX() { return innerX() + Ui.width("Flügelgröße") + 10; }

    private static final int HEADER = 15;

    /** Zeile der Kosmetik-Liste: Ueberschrift einer Kollektion (tiles null) oder eine Reihe Kacheln */
    private record Row(Cosmetics.Collection collection, List<Cosmetics.Item> tiles) {
        boolean header() { return tiles == null; }
    }

    /** Sichtbare Zeile mit ihrer Position */
    private record Placed(Row row, int y) {}

    private record Layout(List<Row> rows, List<Placed> visible, int perRow, int tileW, int tileH, int maxScroll) {}

    private List<Cosmetics.Collection> collections() {
        List<Cosmetics.Collection> out = new ArrayList<>();
        if (!wingsTab && CustomCapes.self() != null) { // eigenes Cape aus dem Launcher
            out.add(new Cosmetics.Collection("custom", "Eigene", List.of(new Cosmetics.Item("custom", "Eigenes")), List.of()));
        }
        out.addAll(Cosmetics.COLLECTIONS);
        return out;
    }

    private Layout layout() {
        int gap = gap(), min = compact() ? 54 : 74;
        int width = listW() - 5; // rechts Platz fuer die Scroll-Anzeige
        int perRow = Math.max(1, (width + gap) / (min + gap));
        int tileW = (width - (perRow - 1) * gap) / perRow;
        int avail = tileBottom() - gridTop();
        // eine Ueberschrift und zwei Reihen sollen ohne Scrollen passen
        int tileH = Math.max(48, Math.min(compact() ? 84 : 118, (avail - HEADER - 2 * gap) / 2));
        List<Row> rows = new ArrayList<>();
        for (Cosmetics.Collection k : collections()) {
            List<Cosmetics.Item> items = wingsTab ? k.wings() : k.capes();
            if (items.isEmpty()) continue;
            rows.add(new Row(k, null));
            if (DeuConfig.get().collapsedCollections.contains(k.id())) continue;
            for (int i = 0; i < items.size(); i += perRow) rows.add(new Row(k, items.subList(i, Math.min(items.size(), i + perRow))));
        }
        // so weit scrollen, bis die letzte Zeile ganz zu sehen ist
        int maxScroll = rows.size(), used = -gap;
        while (maxScroll > 0) {
            used += (rows.get(maxScroll - 1).header() ? HEADER : tileH) + gap;
            if (used > avail) break;
            maxScroll--;
        }
        maxScroll = Math.min(maxScroll, Math.max(0, rows.size() - 1));
        cosmeticScroll = Math.max(0, Math.min(cosmeticScroll, maxScroll));
        List<Placed> visible = new ArrayList<>();
        int y = gridTop();
        for (int i = cosmeticScroll; i < rows.size(); i++) {
            int h = rows.get(i).header() ? HEADER : tileH;
            if (y + h > tileBottom()) break;
            visible.add(new Placed(rows.get(i), y));
            y += h + gap;
        }
        return new Layout(rows, visible, perRow, tileW, tileH, maxScroll);
    }

    private static int noneWidth() { return Ui.width("Nichts anlegen") + 28; }

    private void select(String itemId) {
        DeuConfig cfg = DeuConfig.get();
        if (wingsTab) cfg.wings = itemId;
        else cfg.cape = itemId;
        DeuConfig.save();
        Cosmetics.syncToLauncher();
        DeuNetwork.sendHello();
        rebuildWidgets();
    }

    private void changeWingScale(int deltaPercent) {
        DeuConfig cfg = DeuConfig.get();
        int pct = Math.round(cfg.wingScale * 100f) + deltaPercent;
        cfg.wingScale = Math.max(50, Math.min(150, pct)) / 100f;
        DeuConfig.save();
    }

    private void initCosmetics() {
        DeuConfig cfg = DeuConfig.get();
        int toggleW = compact() ? 74 : 104;
        addRenderableWidget(new ToggleButton(innerX() + listW() - toggleW, panelY() + pad(), toggleW, chipH(), cfg.showCosmetics, false, b -> {
            cfg.showCosmetics = !cfg.showCosmetics;
            DeuConfig.save();
            DeuNetwork.sendHello();
            rebuildWidgets();
        }));
        if (wingsTab) {
            int bw = chipH() + 4, bx = sizeButtonsX();
            addRenderableWidget(new IconButton(bx, sizeRowY(), bw, chipH(), null, Component.literal("-"), b -> changeWingScale(-10)));
            addRenderableWidget(new IconButton(bx + bw + 44, sizeRowY(), bw, chipH(), null, Component.literal("+"), b -> changeWingScale(10)));
        }
        // Elytra: Cape-Textur oder Fluegel (unten in der Spielerkarte)
        int ex = previewX() + 5, ey = panelY() + panelH() - pad() - chipH() - 5, ew = previewW() - 10;
        addRenderableWidget(new IconButton(ex, ey, ew, chipH(), cfg.elytraWings ? Ui.I_WINGS : Ui.I_CAPE,
                Component.literal(cfg.elytraWings ? "Elytra: Flügel" : "Elytra: Cape"), b -> {
            cfg.elytraWings = !cfg.elytraWings;
            DeuConfig.save();
            rebuildWidgets();
        }));
        tabTips.add(new Tip(ex, ey, ew, chipH(), "Mit angelegter Elytra: Elytra in Cape-Textur oder deine Flügel mit Fluganimationen."));
        String selected = wingsTab ? cfg.wings : cfg.cape;
        addRenderableWidget(new IconButton(innerX(), panelY() + pad(), noneWidth(), chipH(), Ui.I_CLOSE, Component.literal("Nichts anlegen"),
                b -> select("")).selected(selected.isEmpty()));
        // unsichtbare Knoepfe ueber Ueberschriften und Kacheln (gezeichnet wird in drawCosmetics)
        Layout layout = layout();
        for (Placed p : layout.visible()) {
            Row row = p.row();
            if (row.header()) {
                String id = row.collection().id();
                addRenderableWidget(new IconButton(innerX(), p.y(), listW() - 5, HEADER, null, Component.empty(), Style.GHOST, 0, b -> {
                    if (!cfg.collapsedCollections.remove(id)) cfg.collapsedCollections.add(id);
                    DeuConfig.save();
                    rebuildWidgets();
                }));
                continue;
            }
            for (int i = 0; i < row.tiles().size(); i++) {
                Cosmetics.Item it = row.tiles().get(i);
                int x = innerX() + i * (layout.tileW() + gap());
                addRenderableWidget(new IconButton(x, p.y(), layout.tileW(), layout.tileH(), null, Component.empty(), Style.GHOST, 0, b -> select(it.id())));
            }
        }
    }

    private void drawCosmetics(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        DeuConfig cfg = DeuConfig.get();
        String selected = wingsTab ? cfg.wings : cfg.cape;
        Layout layout = layout();
        int tw = layout.tileW(), th = layout.tileH();
        for (Placed p : layout.visible()) {
            Row row = p.row();
            int y = p.y();
            if (row.header()) {
                // Ueberschrift: Pfeil, Name, Anzahl
                boolean closed = cfg.collapsedCollections.contains(row.collection().id());
                boolean hover = mouseX >= innerX() && mouseX < innerX() + listW() - 5 && mouseY >= y && mouseY < y + HEADER;
                DeuRender.roundRect(g, innerX(), y, listW() - 5, HEADER, 4, hover ? Ui.CARD_HOVER : Ui.CARD);
                g.pose().pushMatrix();
                g.pose().translate(innerX() + 9, y + HEADER / 2f);
                if (!closed) g.pose().rotate((float) (Math.PI / 2));
                Ui.icon(g, Ui.I_CHEV_R, -4, -4, 8, Ui.TEXT_DIM);
                g.pose().popMatrix();
                Ui.boldText(g, row.collection().name(), innerX() + 18, y + (HEADER - 8) / 2, Ui.TEXT);
                String count = String.valueOf((wingsTab ? row.collection().wings() : row.collection().capes()).size());
                Ui.text(g, count, innerX() + listW() - 11 - Ui.width(count), y + (HEADER - 8) / 2, Ui.TEXT_DIM);
                continue;
            }
            for (int i = 0; i < row.tiles().size(); i++) {
                Cosmetics.Item it = row.tiles().get(i);
                int x = innerX() + i * (tw + gap());
                boolean sel = it.id().equals(selected);
                boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + th;
                if (sel) DeuRender.roundRect(g, x - 1, y - 1, tw + 2, th + 2, 6, Ui.ACCENT);
                DeuRender.roundRect(g, x, y, tw, th, 5, hover ? Ui.CARD_HOVER : Ui.CARD);
                int art = th - 16;
                if (wingsTab) {
                    int s = Math.min((tw - 6) / 2, art - 4);
                    Ui.wingPair(g, Cosmetics.wingsUi(it.id()), x + tw / 2, y + 3 + (art - s) / 2, s);
                } else {
                    int ch = Math.min(art - 6, (tw - 10) * 16 / 10), cw = ch * 10 / 16;
                    Ui.capeFront(g, it.id(), x + (tw - cw) / 2, y + 3 + (art - ch) / 2, cw, ch);
                }
                Ui.centered(g, Ui.ellipsize(it.name(), tw - 6), x + tw / 2, y + th - 12, sel ? Ui.GOLD : Ui.TEXT);
            }
        }
        // Scroll-Anzeige
        if (layout.maxScroll() > 0) {
            int trackH = tileBottom() - gridTop(), total = layout.rows().size();
            int barH = Math.max(16, trackH * (total - layout.maxScroll()) / total);
            int barY = gridTop() + (trackH - barH) * cosmeticScroll / layout.maxScroll();
            DeuRender.roundRect(g, innerX() + listW() - 2, barY, 2, barH, 1, 0x55FFFFFF);
        }
        if (wingsTab) {
            int ty = sizeRowY() + (chipH() - 8) / 2, bw = chipH() + 4, bx = sizeButtonsX();
            Ui.text(g, "Flügelgröße", innerX(), ty, Ui.TEXT_DIM);
            Ui.centered(g, Math.round(cfg.wingScale * 100f) + " %", bx + bw + 22, ty, Ui.TEXT);
        }

        // Dein Spieler
        int px = previewX(), py = panelY() + pad(), pw = previewW(), ph = panelH() - 2 * pad();
        DeuRender.roundRect(g, px, py, pw, ph, 5, Ui.CARD);
        Ui.boldText(g, "DEIN SPIELER", px + (pw - Ui.boldWidth("DEIN SPIELER")) / 2, py + 7, Ui.TEXT_DIM);
        PlayerPreview.draw(g, px + 2, py + 19, px + pw - 2, py + ph - chipH() - 19, previewYaw);
        Ui.centered(g, "Ziehen zum Drehen", px + pw / 2, py + ph - chipH() - 16, Ui.TEXT_DIM);
    }

    /** Flaeche zum Drehen: die Spielerkarte ohne den Elytra-Knopf unten */
    private boolean overPreview(double x, double y) {
        return tab == Tab.COSMETICS && x >= previewX() && x < previewX() + previewW()
                && y >= panelY() + pad() && y < panelY() + panelH() - pad() - chipH() - 6;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (overPreview(e.x(), e.y())) {
            turning = true;
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!turning) return super.mouseDragged(e, dx, dy);
        previewYaw = (previewYaw + (float) dx * 2f) % 360f;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        turning = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.MODS && scrollY != 0) {
            scrollRow += scrollY < 0 ? 1 : -1;
            layoutCards();
            return true;
        }
        if (tab == Tab.COSMETICS && scrollY != 0 && mouseX < previewX()) {
            cosmeticScroll += scrollY < 0 ? 1 : -1;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /* ================= Zeichnen ================= */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta); // Panorama/Deu-Hintergrund bzw. Spielwelt
        g.fillGradient(0, 0, this.width, this.height, 0x6007080A, 0x9007080A);
    }

    /** Kopfzeile: Emblem, Titel und Uhr links, Profil rechts */
    private void drawHeader(GuiGraphicsExtractor g) {
        int x = panelX(), y = topY(), h = headerH(), ty = y + (h - 8) / 2;
        DeuRender.drawEmblem(g, x, y, h, 255);
        String title = tab == Tab.MODS ? "MOD-MENÜ" : "KOSMETIK";
        Ui.boldText(g, title, x + h + 6, ty, Ui.TEXT);

        String name = this.minecraft.getUser().getName();
        int nameW = Ui.boldWidth(name) + h + 8;
        int nx = x + panelW() - nameW;
        DeuRender.roundRect(g, nx, y, nameW, h, 4, Ui.FIELD);
        int face = h - 8;
        PlayerFaceExtractor.extractRenderState(g, Ui.selfSkin(), nx + 4, y + 4, face);
        Ui.boldText(g, name, nx + face + 9, ty, Ui.TEXT);

        String time = LocalDateTime.now().format(CLOCK);
        int cx = x + h + 6 + Ui.boldWidth(title) + 10, cw = Ui.width(time) + 24;
        if (cx + cw < nx - 6) {
            DeuRender.roundRect(g, cx, y, cw, h, 4, Ui.FIELD);
            Ui.icon(g, Ui.I_MOON, cx + 7, y + (h - 9) / 2, 9, Ui.TEXT_DIM);
            Ui.text(g, time, cx + 19, ty, Ui.TEXT_DIM);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        tipText = null;
        drawHeader(g);
        DeuRender.roundRect(g, panelX(), panelY(), panelW(), panelH(), 7, Ui.BG);
        if (tab == Tab.MODS) drawCards(g, mouseX, mouseY);
        else drawCosmetics(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
        for (Tip t : tabTips) {
            if (mouseX >= t.x() && mouseX < t.x() + t.w() && mouseY >= t.y() && mouseY < t.y() + t.h()) tipText = t.text();
        }
        if (tipText != null) Ui.tooltip(g, mouseX, mouseY, tipText, 220, this.width, this.height);
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
