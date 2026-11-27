package eu.deusmp.deucore.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.hud.HudRenderer;
import de.erik.glasshud.hud.widgets.Widget;
import de.erik.glasshud.hud.widgets.Widgets;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Features;
import eu.deusmp.deucore.screen.Ui.IconButton;
import eu.deusmp.deucore.screen.Ui.IconButton.Style;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Elemente direkt auf dem Bildschirm mit der Maus verschieben; das Mausrad aendert die Groesse,
 * die rechte Maustaste oeffnet ein Menue (Anzeigen hinzufuegen oder ausblenden, Raster, Einstellungen).
 * Beim Ziehen rasten Elemente an Raendern, Mitte, Raster und aneinander ein und halten dabei immer Abstand.
 */
public class HudMoveScreen extends Screen {
    private static final int SNAP = 5, EDGE = 4, GAP = 4, GRID = 10;
    private static final int ROW = 13, HEAD = 13, LINE = 5, PAD = 4;
    private final Screen parent;
    private String dragId;
    private int grabX, grabY;
    /** Hilfslinien beim Einrasten (Bildschirmkoordinate), -1 = keine */
    private int guideX = -1, guideY = -1;

    /** Eintrag des Rechtsklick-Menues: action null = Ueberschrift, label null = Trennlinie */
    private record Entry(String label, String icon, Runnable action) {
        int height() { return label == null ? LINE : action == null ? HEAD : ROW; }
    }

    private final List<Entry> menu = new ArrayList<>();
    private int menuX, menuY, menuW, menuH;
    /** Element, zu dem das Menue gehoert (null = freie Flaeche) */
    private String menuId;
    /** Gerade hinzugefuegtes Element: wird an die Klickstelle gesetzt, sobald seine Groesse bekannt ist */
    private String placeId;
    private int placeX, placeY;

    public HudMoveScreen(Screen parent) {
        super(Component.literal("HUD bearbeiten"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        menu.clear();
        int cx = this.width / 2, y = this.height / 2 + 6;
        addRenderableWidget(new IconButton(cx - 104, y, 100, 20, null, Component.literal("Zurücksetzen"), b -> reset()));
        addRenderableWidget(new IconButton(cx + 4, y, 100, 20, null, Component.literal("Fertig"), Style.ACCENT, 0, b -> onClose()));
    }

    private void reset() {
        for (Widget w : Widgets.ALL) {
            HudConfig.W now = HudConfig.get().w(w.id), def = w.defaults();
            now.ax = def.ax;
            now.ay = def.ay;
            now.offX = def.offX;
            now.offY = def.offY;
            now.scale = def.scale;
        }
        HudConfig.save();
    }

    /** Oberstes Element unter der Maus (zuletzt gezeichnet liegt oben) */
    private String widgetAt(double x, double y) {
        String hit = null;
        for (Widget w : Widgets.ALL) {
            HudRenderer.Rect r = HudRenderer.bounds(w.id);
            if (r != null && r.contains(x, y)) hit = w.id;
        }
        return hit;
    }

    /* ================= Rechtsklick-Menue ================= */

    private void openMenu(int x, int y) {
        menu.clear();
        menuId = widgetAt(x, y);
        if (menuId != null) {
            String id = menuId;
            Widget widget = widget(id);
            menu.add(new Entry(widget.name, null, null));
            menu.add(new Entry("Einstellungen", Ui.I_GEAR, () -> openSettings(id)));
            menu.add(new Entry("Größe zurücksetzen", Ui.I_ZOOM, () -> {
                HudConfig.get().w(id).scale = widget(id).defaults().scale;
                HudConfig.save();
            }));
            menu.add(new Entry("Ausblenden", Ui.I_CLOSE, () -> {
                HudConfig.get().w(id).enabled = false;
                HudConfig.save();
            }));
        } else {
            List<Widget> hidden = Widgets.ALL.stream().filter(w -> !HudConfig.get().w(w.id).enabled).toList();
            menu.add(new Entry(hidden.isEmpty() ? "Alle Anzeigen sind aktiv" : "Anzeige hinzufügen", null, null));
            for (Widget w : hidden) {
                menu.add(new Entry(w.name, Ui.I_PLUS, () -> {
                    HudConfig.get().w(w.id).enabled = true;
                    HudConfig.save();
                    placeId = w.id;
                    placeX = x;
                    placeY = y;
                }));
            }
        }
        menu.add(new Entry(null, null, null));
        menu.add(new Entry(HudConfig.get().grid ? "Raster ausblenden" : "Raster anzeigen", Ui.I_WINDOW, () -> {
            HudConfig.get().grid = !HudConfig.get().grid;
            HudConfig.save();
        }));
        menu.add(new Entry("Mod-Menü", Ui.I_GRID, () -> this.minecraft.gui.setScreen(new DeuModMenuScreen(this))));
        menu.add(new Entry("Minecraft-Optionen", Ui.I_GEAR,
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));
        menu.add(new Entry("Alles zurücksetzen", Ui.I_HOURGLASS, this::reset));
        menu.add(new Entry("Fertig", Ui.I_CHECK, this::onClose));

        menuW = 0;
        menuH = 2 * PAD;
        for (Entry e : menu) {
            menuH += e.height();
            if (e.label() != null) menuW = Math.max(menuW, e.action() == null ? Ui.boldWidth(e.label()) + 14 : Ui.width(e.label()) + 28);
        }
        menuW = Math.max(110, menuW);
        // an der Maus oeffnen, aber ganz auf dem Bildschirm bleiben
        menuX = Math.max(2, Math.min(x, this.width - menuW - 2));
        menuY = Math.max(2, Math.min(y, this.height - menuH - 2));
    }

    private static Widget widget(String id) {
        return Widgets.ALL.stream().filter(w -> w.id.equals(id)).findFirst().orElseThrow();
    }

    private void openSettings(String id) {
        Features.all(p -> new DeuModMenuScreen(p, DeuModMenuScreen.Tab.COSMETICS)).stream()
                .filter(f -> f.id().equals("hud_" + id)).findFirst()
                .ifPresent(f -> this.minecraft.gui.setScreen(new FeatureSettingsScreen(this, f)));
    }

    /** Eintrag unter der Maus, null = keiner (Ueberschrift, Linie oder ausserhalb) */
    private Entry entryAt(double x, double y) {
        if (x < menuX || x >= menuX + menuW) return null;
        int top = menuY + PAD;
        for (Entry e : menu) {
            if (y >= top && y < top + e.height()) return e.action() != null ? e : null;
            top += e.height();
        }
        return null;
    }

    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.nextStratum(); // ueber Anzeigen und Knoepfen
        DeuRender.roundRect(g, menuX, menuY, menuW, menuH, 5, 0xFF16181D);
        Entry hover = entryAt(mouseX, mouseY);
        int y = menuY + PAD;
        for (Entry e : menu) {
            if (e.label() == null) {
                g.fill(menuX + 6, y + LINE / 2, menuX + menuW - 6, y + LINE / 2 + 1, Ui.LINE);
            } else if (e.action() == null) {
                Ui.boldText(g, e.label(), menuX + 7, y + 2, Ui.TEXT_DIM);
            } else {
                if (e == hover) DeuRender.roundRect(g, menuX + 3, y, menuW - 6, ROW, 3, 0xFF2A2E35);
                Ui.icon(g, e.icon(), menuX + 8, y + 2, 9, e == hover ? Ui.GOLD : Ui.TEXT_DIM);
                Ui.text(g, e.label(), menuX + 22, y + 3, Ui.TEXT);
            }
            y += e.height();
        }
    }

    /* ================= Maus und Tasten ================= */

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!menu.isEmpty()) {
            Entry entry = entryAt(e.x(), e.y());
            boolean inside = e.x() >= menuX && e.x() < menuX + menuW && e.y() >= menuY && e.y() < menuY + menuH;
            if (inside && !right) {
                if (entry != null) {
                    menu.clear();
                    entry.action().run();
                }
                return true;
            }
            menu.clear();
            if (!right) return true; // Klick daneben schliesst nur
        }
        if (right) {
            openMenu((int) e.x(), (int) e.y());
            return true;
        }
        if (super.mouseClicked(e, doubleClick)) return true;
        String id = widgetAt(e.x(), e.y());
        if (id == null || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        HudRenderer.Rect r = HudRenderer.bounds(id);
        dragId = id;
        grabX = (int) e.x() - r.x();
        grabY = (int) e.y() - r.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragId == null) return super.mouseDragged(e, dx, dy);
        drag((int) e.x(), (int) e.y());
        return true;
    }

    private void drag(int mouseX, int mouseY) {
        HudRenderer.Rect r = HudRenderer.bounds(dragId);
        if (r == null) return;
        int rawX = mouseX - grabX, rawY = mouseY - grabY;
        List<HudRenderer.Rect> others = others(dragId);
        Snap sx = snap(rawX, r.w(), this.width, others, true), sy = snap(rawY, r.h(), this.height, others, false);
        int[] pos = separate(sx.pos(), sy.pos(), r.w(), r.h(), others);
        guideX = pos[0] == sx.pos() ? sx.line() : -1;
        guideY = pos[1] == sy.pos() ? sy.line() : -1;
        moveTo(dragId, r, pos[0], pos[1]);
    }

    /** Nur fuer Testbilder (DemoMode): Element an eine Stelle ziehen, wie mit der Maus (linke obere Ecke) */
    public void showDrag(String id, int x, int y) {
        menu.clear();
        dragId = id;
        grabX = grabY = 0;
        drag(x, y);
        HudRenderer.Rect r = HudRenderer.bounds(id);
        eu.deusmp.deucore.DeuCore.LOGGER.info("[DeuCore demo] {} nach {},{} -> {}", id, x, y, r);
    }

    private List<HudRenderer.Rect> others(String id) {
        List<HudRenderer.Rect> out = new ArrayList<>();
        for (Widget w : Widgets.ALL) {
            HudRenderer.Rect r = HudRenderer.bounds(w.id);
            if (r != null && !w.id.equals(id)) out.add(r);
        }
        return out;
    }

    private record Snap(int pos, int line) {}

    /** Eine Achse einrasten: an Rand, Mitte oder einem anderen Element, sonst am Raster */
    private Snap snap(int raw, int size, int screen, List<HudRenderer.Rect> others, boolean horizontal) {
        List<Snap> targets = new ArrayList<>();
        targets.add(new Snap(EDGE, EDGE));
        targets.add(new Snap(screen - size - EDGE, screen - EDGE - 1));
        targets.add(new Snap((screen - size) / 2, screen / 2));
        for (HudRenderer.Rect o : others) {
            int start = horizontal ? o.x() : o.y(), end = start + (horizontal ? o.w() : o.h());
            targets.add(new Snap(start, start));              // buendig links bzw. oben
            targets.add(new Snap(end - size, end - 1));       // buendig rechts bzw. unten
            targets.add(new Snap(end + GAP, -1));             // daneben bzw. darunter, mit Abstand
            targets.add(new Snap(start - GAP - size, -1));    // davor bzw. darueber, mit Abstand
        }
        Snap best = null;
        for (Snap t : targets) {
            if (Math.abs(t.pos() - raw) < SNAP && (best == null || Math.abs(t.pos() - raw) < Math.abs(best.pos() - raw))) best = t;
        }
        if (best != null) return best;
        return new Snap(HudConfig.get().grid ? Math.round(raw / (float) GRID) * GRID : raw, -1);
    }

    /** Schiebt das Element so weit, dass zu allen anderen mindestens der Abstand bleibt (kuerzester Weg im Bild) */
    private int[] separate(int x, int y, int w, int h, List<HudRenderer.Rect> others) {
        x = Math.max(0, Math.min(this.width - w, x));
        y = Math.max(0, Math.min(this.height - h, y));
        for (int round = 0; round < 6; round++) {
            HudRenderer.Rect hit = null;
            for (HudRenderer.Rect o : others) {
                if (tooClose(x, y, w, h, o)) {
                    hit = o;
                    break;
                }
            }
            if (hit == null) break;
            int[][] moves = {{hit.x() - GAP - w, y}, {hit.x() + hit.w() + GAP, y}, {x, hit.y() - GAP - h}, {x, hit.y() + hit.h() + GAP}};
            int[] best = null;
            int bestCost = Integer.MAX_VALUE;
            for (int[] m : moves) {
                if (m[0] < 0 || m[1] < 0 || m[0] > this.width - w || m[1] > this.height - h) continue;
                // Ziele, die schon wieder an einem anderen Element liegen, nur wenn es nicht anders geht
                int cost = Math.abs(m[0] - x) + Math.abs(m[1] - y);
                for (HudRenderer.Rect o : others) if (tooClose(m[0], m[1], w, h, o)) cost += 10_000;
                if (cost < bestCost) {
                    best = m;
                    bestCost = cost;
                }
            }
            if (best == null) break;
            x = best[0];
            y = best[1];
        }
        return new int[]{x, y};
    }

    private static boolean tooClose(int x, int y, int w, int h, HudRenderer.Rect o) {
        return x < o.x() + o.w() + GAP && x + w + GAP > o.x() && y < o.y() + o.h() + GAP && y + h + GAP > o.y();
    }

    /** Setzt die linke obere Ecke eines Elements (innerhalb des Bildschirms) */
    private void moveTo(String id, HudRenderer.Rect r, int x, int y) {
        x = Math.max(0, Math.min(this.width - r.w(), x));
        y = Math.max(0, Math.min(this.height - r.h(), y));
        HudConfig.W w = HudConfig.get().w(id);
        // Anker nach Bildschirmdrittel: das Element bleibt auch bei anderer Fenstergroesse an seiner Seite
        w.ax = anchor(x + r.w() / 2, this.width);
        w.ay = anchor(y + r.h() / 2, this.height);
        w.offX = x - w.ax * (this.width - r.w()) / 2;
        w.offY = y - w.ay * (this.height - r.h()) / 2;
    }

    private static int anchor(int center, int size) {
        return center < size / 3 ? 0 : center > size * 2 / 3 ? 2 : 1;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragId != null) {
            dragId = null;
            guideX = guideY = -1;
            HudConfig.save();
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        String id = menu.isEmpty() ? widgetAt(mouseX, mouseY) : null;
        if (id == null || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        HudConfig.W w = HudConfig.get().w(id);
        int pct = Math.round(w.scale * 100) + (scrollY > 0 ? 5 : -5);
        w.scale = Math.max(50, Math.min(300, pct)) / 100f;
        HudConfig.save();
        return true;
    }

    /* ================= Zeichnen ================= */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (this.minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
        g.fill(0, 0, this.width, this.height, 0x50000000); // Welt bleibt sichtbar
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (HudConfig.get().grid) {
            for (int x = GRID; x < this.width; x += GRID) g.fill(x, 0, x + 1, this.height, 0x12FFFFFF);
            for (int y = GRID; y < this.height; y += GRID) g.fill(0, y, this.width, y + 1, 0x12FFFFFF);
            g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x30FFFFFF);
            g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x30FFFFFF);
        }
        if (guideX >= 0) g.fill(guideX, 0, guideX + 1, this.height, 0x902EFF7A);
        if (guideY >= 0) g.fill(0, guideY, this.width, guideY + 1, 0x902EFF7A);
        HudRenderer.renderAll(g, this.minecraft, true);
        if (placeId != null) {
            HudRenderer.Rect r = HudRenderer.bounds(placeId);
            if (r != null) {
                int[] pos = separate(placeX, placeY, r.w(), r.h(), others(placeId));
                moveTo(placeId, r, pos[0], pos[1]);
                HudConfig.save();
            }
            placeId = null;
        }
        // Element unter der Maus (bzw. des Menues) leicht aufhellen - ohne Rahmen
        String hover = !menu.isEmpty() ? menuId : dragId != null ? dragId : widgetAt(mouseX, mouseY);
        HudRenderer.Rect r = hover == null ? null : HudRenderer.bounds(hover);
        if (r != null) {
            float size = HudConfig.get().w(hover).scale;
            DeuRender.roundRect(g, r.x(), r.y(), r.w(), r.h(), Math.round(7 * size), 0x1EFFFFFF);
        }
        Ui.centered(g, "Ziehen zum Verschieben, Mausrad ändert die Größe", this.width / 2, this.height / 2 - 22, Ui.TEXT);
        Ui.centered(g, "Rechtsklick öffnet das Menü", this.width / 2, this.height / 2 - 10, Ui.TEXT_DIM);
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (!menu.isEmpty()) drawMenu(g, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        if (!menu.isEmpty()) { // Escape schliesst zuerst das Menue
            menu.clear();
            return;
        }
        HudConfig.save();
        this.minecraft.gui.setScreen(parent);
    }

    /** Nur fuer Testbilder (DemoMode): Menue ohne Maus oeffnen */
    public void showMenu(int x, int y) {
        openMenu(x, y);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
