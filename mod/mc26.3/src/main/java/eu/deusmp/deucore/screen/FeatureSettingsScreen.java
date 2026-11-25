package eu.deusmp.deucore.screen;

import eu.deusmp.deucore.Crosshair;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Features.Feature;
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

    public FeatureSettingsScreen(Screen parent, Feature feature) {
        super(Component.literal(feature.name()));
        this.parent = parent;
        this.feature = feature;
    }

    private int pw() { return Math.min(440, this.width - 30); }
    private int ph() { return Math.min(340, this.height - 30); }
    private int px() { return (this.width - pw()) / 2; }
    private int py() { return (this.height - ph()) / 2; }
    private int rowsTop() { return py() + 84; }
    private int perPage() { return Math.max(1, (py() + ph() - 40 - rowsTop()) / ROW_H); }

    @Override
    protected void init() {
        int px = px(), py = py(), pw = pw();
        addRenderableWidget(new IconButton(px + pw - 30, py + 8, 22, 22, Ui.I_CLOSE, Component.empty(), Style.DANGER, 0, b -> onClose()));

        if (feature.hasToggle()) {
            addRenderableWidget(new ToggleButton(px + pw - 128, py + 50, 92, 18, feature.isOn().getAsBoolean(), false, b -> {
                feature.setOn().accept(!feature.isOn().getAsBoolean());
                rebuildWidgets();
            }));
        }

        List<Setting> settings = feature.settings();
        int per = perPage();
        int pages = Math.max(1, (settings.size() + per - 1) / per);
        page = Math.min(page, pages - 1);
        int cw = 120, cx = px + pw - 16 - cw;
        int y = rowsTop();
        for (int i = page * per; i < Math.min(settings.size(), (page + 1) * per); i++) {
            Setting s = settings.get(i);
            if (s.kind() == Kind.STEP) {
                addRenderableWidget(new IconButton(cx, y, 22, 18, Ui.I_MINUS, Component.empty(), b -> { s.cycle(-1); rebuildWidgets(); }));
                addRenderableWidget(new IconButton(cx + 24, y, cw - 48, 18, null, Component.literal(s.display()), Style.GHOST, 0, b -> {}));
                addRenderableWidget(new IconButton(cx + cw - 22, y, 22, 18, Ui.I_PLUS, Component.empty(), b -> { s.cycle(1); rebuildWidgets(); }));
            } else if (s.kind() == Kind.TOGGLE) {
                addRenderableWidget(new ToggleButton(cx, y, cw, 18, s.get().getAsInt() == 1, false, b -> { s.cycle(1); rebuildWidgets(); }));
            } else {
                addRenderableWidget(new IconButton(cx, y, 22, 18, Ui.I_CHEV_L, Component.empty(), b -> { s.cycle(-1); rebuildWidgets(); }));
                addRenderableWidget(new IconButton(cx + 24, y, cw - 48, 18, null, Component.literal(s.display()), Style.GHOST, 0, b -> { s.cycle(1); rebuildWidgets(); }));
                addRenderableWidget(new IconButton(cx + cw - 22, y, 22, 18, Ui.I_CHEV_R, Component.empty(), b -> { s.cycle(1); rebuildWidgets(); }));
            }
            y += ROW_H;
        }

        int by = py + ph() - 30;
        if (pages > 1) {
            addRenderableWidget(new IconButton(px + 16, by, 22, 20, null, Component.literal("◀"), b -> { page--; rebuildWidgets(); })).active = page > 0;
            addRenderableWidget(new IconButton(px + 42, by, 22, 20, null, Component.literal("▶"), b -> { page++; rebuildWidgets(); })).active = page < pages - 1;
        }
        if (feature.customScreen() != null) {
            IconButton open = new IconButton(px + pw - 16 - 230, by, 150, 20, Ui.I_SPARKLE, Component.literal(feature.id().equals("crosshair") ? "Form zeichnen" : feature.category() == eu.deusmp.deucore.Features.Category.COSMETICS ? "Kosmetik öffnen" : "Eigenes Menü öffnen"),
                    Style.ACCENT, 0, b -> {
                Screen s = feature.customScreen().apply(this);
                if (s != null) this.minecraft.gui.setScreen(s);
            });
            // ausgeschaltetes Modul: sein eigenes Menue laesst sich nicht oeffnen
            open.active = !feature.hasToggle() || feature.isOn().getAsBoolean();
            addRenderableWidget(open);
        }
        addRenderableWidget(new IconButton(px + pw - 16 - 72, by, 72, 20, null, Component.literal("Zurück"), b -> onClose()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta); // Panorama/Deu-Hintergrund bzw. Spielwelt
        g.fillGradient(0, 0, this.width, this.height, 0x80050806, 0xA0050806);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int px = px(), py = py(), pw = pw(), ph = ph();
        DeuRender.roundRect(g, px, py, pw, ph, 6, Ui.BG);
        // Kopf
        DeuRender.roundRect(g, px + 12, py + 10, 30, 30, 5, Ui.CARD);
        Ui.iconCentered(g, feature.icon(), px + 27, py + 25, 18, Ui.GOLD);
        Ui.title(g, feature.name(), px + 50, py + 12, 1.4f, Ui.TEXT);
        Ui.text(g, feature.category().label, px + 50, py + 29, Ui.TEXT_DIM);
        int ty = py + 50;
        for (FormattedCharSequence line : this.font.split(Ui.ui(feature.description()), pw - (feature.hasToggle() ? 170 : 40))) {
            g.text(this.font, line, px + 16, ty, Ui.TEXT_DIM, false);
            ty += 10;
        }
        g.fill(px + 12, rowsTop() - 8, px + pw - 12, rowsTop() - 7, Ui.LINE);

        List<Setting> settings = feature.settings();
        int per = perPage();
        int y = rowsTop();
        for (int i = page * per; i < Math.min(settings.size(), (page + 1) * per); i++) {
            Ui.text(g, settings.get(i).label(), px + 16, y + 5, Ui.TEXT);
            y += ROW_H;
        }
        if (settings.isEmpty()) {
            Ui.text(g, feature.customScreen() != null ? "Alle Einstellungen findest du im eigenen Menü." : "Keine weiteren Einstellungen.", px + 16, rowsTop() + 4, Ui.TEXT_DIM);
        }
        // Fadenkreuz-Vorschau
        if (feature.id().equals("crosshair")) {
            int cx = px + pw / 2, cy = py + ph - 60;
            DeuRender.roundRect(g, cx - 24, cy - 16, 48, 32, 5, 0xFF2A3A20);
            DeuConfig cfg = DeuConfig.get();
            Crosshair.draw(g, cx, cy, cfg.crosshairStyle, Crosshair.colorOf(cfg));
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent); // init() baut die Karten mit neuem Zustand auf
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
