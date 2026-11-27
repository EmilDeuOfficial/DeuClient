package eu.deusmp.deucore.screen;

import eu.deusmp.deucore.Crosshair;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Features.Feature;
import eu.deusmp.deucore.HudStyle;
import eu.deusmp.deucore.Features.Kind;
import eu.deusmp.deucore.Features.Setting;
import eu.deusmp.deucore.screen.Ui.IconButton;
import eu.deusmp.deucore.screen.Ui.IconButton.Style;
import eu.deusmp.deucore.screen.Ui.ToggleButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Einstellungen einer einzelnen Funktion aus dem Mod-Menü (Zahnrad auf der Karte). */
public class FeatureSettingsScreen extends Screen {
    private static final int ROW_H = 24;
    private final Screen parent;
    private final Feature feature;
    private int page;
    private String group; // gewaehlte Unterkategorie
    private boolean applied;
    private final int themeRevision = eu.deusmp.deucore.Theme.revision;

    public FeatureSettingsScreen(Screen parent, Feature feature) {
        super(Component.literal(feature.name()));
        this.parent = parent;
        this.feature = feature;
    }

    private int pw() { return Math.min(400, this.width - 30); }
    private int ph() { return Math.min(300, this.height - 24); }
    private int px() { return (this.width - pw()) / 2; }
    private int py() { return (this.height - ph()) / 2; }
    private List<String> groups() { return feature.settings().stream().map(Setting::group).filter(java.util.Objects::nonNull).distinct().toList(); }
    private int tabH() { return groups().size() > 1 ? 24 : 0; }
    private int rowsTop() { return py() + 78 + tabH(); }
    /** Nur fuer Testbilder (DemoMode): Unterkategorie waehlen */
    public FeatureSettingsScreen showGroup(String name) {
        group = name;
        return this;
    }

    /** Element-Id fuer die Darstellung (HUD-Element, Scout oder Discord), sonst null */
    private String styleId() {
        if (feature.id().startsWith("hud_")) return feature.id().substring(4);
        if (feature.id().equals("discordglass")) return HudStyle.DISCORD;
        return feature.id().equals("glassscout") ? HudStyle.SCOUT : null;
    }
    private boolean crosshair() { return feature.id().equals("crosshair"); }
    private int previewH() { return crosshair() ? 40 : 0; }
    private int perPage() { return Math.max(1, (py() + ph() - 38 - previewH() - rowsTop()) / ROW_H); }

    /** Nur die Einstellungen, die gerade etwas bewirken */
    private List<Setting> visible() {
        boolean tabs = groups().size() > 1;
        return feature.settings().stream().filter(Setting::shown).filter(s -> !tabs || java.util.Objects.equals(s.group(), group)).toList();
    }

    @Override
    protected void init() {
        int px = px(), py = py(), pw = pw();
        addRenderableWidget(new IconButton(px + pw - 30, py + 10, 20, 20, Ui.I_CLOSE, Component.empty(), Style.DANGER, 0, b -> onClose()));

        if (feature.hasToggle()) {
            addRenderableWidget(new ToggleButton(px + pw - 120, py + 11, 84, 18, feature.isOn().getAsBoolean(), false, b -> {
                feature.setOn().accept(!feature.isOn().getAsBoolean());
                rebuildWidgets();
            }));
        }

        List<String> groups = groups();
        if (groups.size() > 1) {
            if (group == null) group = groups.getFirst();
            int tx = px + 12;
            for (String name : groups) {
                int w = Ui.width(name) + 24;
                addRenderableWidget(new IconButton(tx, rowsTop() - 26, w, 18, null, Component.literal(name), name.equals(group) ? Style.NORMAL : Style.GHOST, 0, b -> {
                    group = name;
                    page = 0;
                    rebuildWidgets();
                }).selected(name.equals(group)));
                tx += w + 4;
            }
        }

        List<Setting> settings = visible();
        int per = perPage();
        int pages = Math.max(1, (settings.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        int cw = 116, cx = px + pw - 18 - cw;
        int y = rowsTop() + 1;
        for (int i = page * per; i < Math.min(settings.size(), (page + 1) * per); i++) {
            Setting s = settings.get(i);
            if (s.kind() == Kind.STEP) {
                addRenderableWidget(new IconButton(cx, y, 20, 18, Ui.I_MINUS, Component.empty(), b -> { s.cycle(-1); rebuildWidgets(); }));
                addRenderableWidget(new IconButton(cx + 22, y, cw - 44, 18, null, Component.literal(s.display()), Style.GHOST, 0, b -> {}));
                addRenderableWidget(new IconButton(cx + cw - 20, y, 20, 18, Ui.I_PLUS, Component.empty(), b -> { s.cycle(1); rebuildWidgets(); }));
            } else if (s.kind() == Kind.TOGGLE) {
                addRenderableWidget(new ToggleButton(cx, y, cw, 18, s.get().getAsInt() == 1, false, b -> { s.cycle(1); rebuildWidgets(); }));
            } else {
                addRenderableWidget(new IconButton(cx, y, 20, 18, Ui.I_CHEV_L, Component.empty(), b -> { s.cycle(-1); rebuildWidgets(); }));
                addRenderableWidget(new IconButton(cx + 22, y, cw - 44, 18, null, Component.literal(s.display()), Style.GHOST, 0, b -> { s.cycle(1); rebuildWidgets(); }));
                addRenderableWidget(new IconButton(cx + cw - 20, y, 20, 18, Ui.I_CHEV_R, Component.empty(), b -> { s.cycle(1); rebuildWidgets(); }));
            }
            y += ROW_H;
        }

        int by = py + ph() - 28;
        if (pages > 1) {
            addRenderableWidget(new IconButton(px + 12, by, 20, 20, Ui.I_CHEV_L, Component.empty(), b -> { page--; rebuildWidgets(); })).active = page > 0;
            addRenderableWidget(new IconButton(px + 34, by, 20, 20, Ui.I_CHEV_R, Component.empty(), b -> { page++; rebuildWidgets(); })).active = page < pages - 1;
        }
        if (styleId() != null && HudStyle.GROUP.equals(group) && pages <= 1) {
            addRenderableWidget(new IconButton(px + 12, by, 128, 20, null, Component.literal(applied ? "Übertragen" : "Auf alle übertragen"), b -> {
                HudStyle.applyToAll(styleId());
                applied = true;
                rebuildWidgets();
            }));
        }
        int right = px + pw - 12 - 72;
        addRenderableWidget(new IconButton(right, by, 72, 20, null, Component.literal("Zurück"), b -> onClose()));
        // eigenes Menue: beim Fadenkreuz nur, wenn die Form "Eigene" gewaehlt ist
        boolean custom = feature.customScreen() != null
                && (!crosshair() || DeuConfig.get().crosshairStyle == Crosshair.STYLES.length - 1);
        if (custom) {
            String label = crosshair() ? "Form zeichnen" : feature.category() == eu.deusmp.deucore.Features.Category.COSMETICS ? "Kosmetik öffnen" : "Eigenes Menü öffnen";
            IconButton open = new IconButton(right - 138, by, 132, 20, null, Component.literal(label), Style.ACCENT, 0, b -> {
                Screen s = feature.customScreen().apply(this);
                if (s != null) this.minecraft.gui.setScreen(s);
            });
            // ausgeschaltetes Modul: sein eigenes Menue laesst sich nicht oeffnen
            open.active = !feature.hasToggle() || feature.isOn().getAsBoolean();
            addRenderableWidget(open);
        }
        if (feature.id().startsWith("hud_")) {
            IconButton move = new IconButton(right - 138, by, 132, 20, null, Component.literal("Position verschieben"), Style.ACCENT, 0,
                    b -> this.minecraft.gui.setScreen(new HudMoveScreen(this)));
            move.active = feature.isOn().getAsBoolean();
            addRenderableWidget(move);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta); // Panorama/Deu-Hintergrund bzw. Spielwelt
        g.fillGradient(0, 0, this.width, this.height, 0x8007080A, 0xA007080A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int px = px(), py = py(), pw = pw(), ph = ph();
        DeuRender.roundRect(g, px, py, pw, ph, 8, Ui.BG);
        // Kopf
        DeuRender.roundRect(g, px + 12, py + 10, 30, 30, 6, Ui.CARD);
        Ui.iconCentered(g, feature.icon(), px + 27, py + 25, 18, Ui.GOLD);
        Ui.title(g, feature.name(), px + 50, py + 12, 1.3f, Ui.TEXT);
        Ui.text(g, feature.category().label, px + 50, py + 29, Ui.TEXT_DIM);
        int ty = py + 48;
        for (FormattedCharSequence line : this.font.split(Ui.ui(feature.description()), pw - 28)) {
            g.text(this.font, line, px + 14, ty, Ui.TEXT_DIM, false);
            ty += 10;
            if (ty > rowsTop() - tabH() - 14) break;
        }
        g.fill(px + 12, rowsTop() - tabH() - 6, px + pw - 12, rowsTop() - tabH() - 5, Ui.LINE);

        List<Setting> settings = visible();
        int per = perPage();
        int y = rowsTop();
        for (int i = page * per; i < Math.min(settings.size(), (page + 1) * per); i++) {
            DeuRender.roundRect(g, px + 12, y - 1, pw - 24, ROW_H - 2, 5, Ui.CARD);
            Ui.text(g, settings.get(i).label(), px + 20, y + 6, Ui.TEXT);
            y += ROW_H;
        }
        if (feature.settings().isEmpty()) {
            Ui.text(g, feature.customScreen() != null ? "Alle Einstellungen findest du im eigenen Menü." : "Keine weiteren Einstellungen.", px + 16, rowsTop() + 4, Ui.TEXT_DIM);
        }
        // Fadenkreuz-Vorschau
        if (crosshair()) {
            int cx = px + pw / 2, cy = py + ph - 52;
            DeuRender.roundRect(g, cx - 24, cy - 16, 49, 33, 5, Ui.CARD);
            DeuConfig cfg = DeuConfig.get();
            Crosshair.draw(g, cx, cy, cfg.crosshairStyle, Crosshair.colorOf(cfg));
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent); // init() baut die Karten mit neuem Zustand auf
        // neue Themefarbe: Texturen (Trennlinien, Reiter, Namensicon) einmal neu laden
        if (themeRevision != eu.deusmp.deucore.Theme.revision) this.minecraft.reloadResourcePacks();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
