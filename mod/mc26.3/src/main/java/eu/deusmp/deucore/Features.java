package eu.deusmp.deucore;

import com.mojang.blaze3d.platform.InputConstants;
import eu.deusmp.deucore.GlassMods.GlassMod;
import eu.deusmp.deucore.net.DeuNetwork;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Alle Funktionen des DeuClients, wie sie im Mod-Menü als Karten erscheinen.
 * Jede Funktion hat optional einen An/Aus-Schalter, Einstellungen und ein eigenes Menü.
 */
public final class Features {
    private Features() {}

    public enum Category {
        ALL("Alle"), FAVORITES("Favoriten"), DEU("Optik"), CLIENT("Client"), COSMETICS("Kosmetik"), GLASS("Module");
        public final String label;
        Category(String label) { this.label = label; }
    }

    public enum Kind { TOGGLE, CYCLE, STEP }

    /** Eine einzelne Einstellung (Zeile im Einstellungs-Bildschirm) */
    public record Setting(String label, Kind kind, String[] names, int min, int max, int step, String suffix, IntSupplier get, IntConsumer set,
                          BooleanSupplier visible, String group) {
        public Setting(String label, Kind kind, String[] names, int min, int max, int step, String suffix, IntSupplier get, IntConsumer set) {
            this(label, kind, names, min, max, step, suffix, get, set, null, null);
        }

        public Setting(String label, Kind kind, String[] names, int min, int max, int step, String suffix, IntSupplier get, IntConsumer set,
                       BooleanSupplier visible) {
            this(label, kind, names, min, max, step, suffix, get, set, visible, null);
        }

        /** Dieselbe Einstellung in einer Unterkategorie */
        public Setting in(String group) {
            return new Setting(label, kind, names, min, max, step, suffix, get, set, visible, group);
        }

        /** false: Einstellung wird ausgeblendet, weil sie gerade nichts bewirkt */
        public boolean shown() { return visible == null || visible.getAsBoolean(); }

        public String display() {
            int v = get.getAsInt();
            if (v < 0) return "-";
            return switch (kind) {
                case TOGGLE -> v != 0 ? "§aAn" : "§cAus";
                case CYCLE -> names[Math.floorMod(v, names.length)];
                case STEP -> v + suffix;
            };
        }

        /** Klick: Schalter umlegen bzw. zum nächsten Wert (dir = +1/-1) */
        public void cycle(int dir) {
            int v = get.getAsInt();
            int next = switch (kind) {
                case TOGGLE -> v != 0 ? 0 : 1;
                case CYCLE -> Math.floorMod(v + dir, names.length);
                case STEP -> Math.max(min, Math.min(max, v + dir * step));
            };
            set.accept(next);
        }
    }

    public record Feature(String id, String name, String description, Category category, String icon, boolean isNew,
                          BooleanSupplier isOn, Consumer<Boolean> setOn, List<Setting> settings, Function<Screen, Screen> customScreen) {
        public boolean hasToggle() { return isOn != null && setOn != null; }
        public boolean isFavorite() { return DeuConfig.get().favorites.contains(id); }
        public void toggleFavorite() {
            List<String> fav = DeuConfig.get().favorites;
            if (!fav.remove(id)) fav.add(id);
            DeuConfig.save();
        }
    }

    /* ---------------- Helfer für Felder in DeuConfig ---------------- */

    private static Field field(String name) {
        try {
            return DeuConfig.class.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BooleanSupplier bool(String name) {
        Field f = field(name);
        return () -> { try { return f.getBoolean(DeuConfig.get()); } catch (IllegalAccessException e) { return false; } };
    }

    private static Consumer<Boolean> setBool(String name, boolean sync) {
        Field f = field(name);
        return v -> {
            try { f.setBoolean(DeuConfig.get(), v); } catch (IllegalAccessException ignored) {}
            DeuConfig.save();
            if (sync) DeuNetwork.sendHello();
        };
    }

    private static Setting toggle(String label, String name) {
        BooleanSupplier g = bool(name);
        Consumer<Boolean> s = setBool(name, false);
        return new Setting(label, Kind.TOGGLE, null, 0, 1, 1, "", () -> g.getAsBoolean() ? 1 : 0, v -> s.accept(v != 0));
    }

    private static Setting cycle(String label, String name, String... names) {
        Field f = field(name);
        return new Setting(label, Kind.CYCLE, names, 0, names.length - 1, 1, "",
                () -> { try { return f.getInt(DeuConfig.get()); } catch (IllegalAccessException e) { return 0; } },
                v -> { try { f.setInt(DeuConfig.get(), v); } catch (IllegalAccessException ignored) {} DeuConfig.save(); });
    }

    private static final int[] ZOOM_KEYS = {InputConstants.KEY_C, InputConstants.KEY_V, InputConstants.KEY_X, InputConstants.KEY_Z, InputConstants.KEY_B, InputConstants.KEY_LALT};
    private static final String[] ZOOM_KEY_NAMES = {"C", "V", "X", "Z", "B", "Alt"};

    private static Feature deu(String id, String name, String desc, String icon, String field, Setting... extra) {
        return new Feature(id, name, desc, Category.DEU, icon, false, bool(field), setBool(field, false), List.of(extra), null);
    }

    private static final java.util.Map<String, String> HUD_DESC = java.util.Map.of(
            "coords", "Zeigt deine Koordinaten und Blickrichtung.",
            "fps", "Zeigt die Bilder pro Sekunde mit Diagramm.",
            "ping", "Zeigt deinen Ping zum Server.",
            "speed", "Zeigt deine Geschwindigkeit.",
            "compass", "Kompassleiste mit Himmelsrichtungen.",
            "clock", "Zeigt die Uhrzeit und die Spielzeit.",
            "potions", "Zeigt aktive Effekte mit Zeitbalken.",
            "armor", "Zeigt deine Rüstung und das Werkzeug in der Hand mit Haltbarkeit.",
            "keys", "Zeigt gedrückte Tasten und Klicks pro Sekunde.");
    private static final java.util.Map<String, String> HUD_ICON = java.util.Map.of(
            "coords", "mod_location", "fps", "mod_fps", "ping", "mod_ping", "speed", "mod_speed", "compass", "mod_compass",
            "clock", "mod_time", "potions", "mod_color-palette", "armor", "mod_armor", "keys", "mod_keyboard");

    /** Baut die Liste aller Funktionen (Glass-Mods nur, wenn installiert) */
    public static List<Feature> all(Function<Screen, Screen> cosmeticsScreen) {
        List<Feature> list = new ArrayList<>();
        // ---- Client
        list.add(new Feature("f3name", "F3-Name", "Zeigt \"DeuClient\" und deinen Spielernamen oben im F3-Bildschirm.",
                Category.CLIENT, "mod_info", true, bool("f3Name"), setBool("f3Name", false), List.of(), null));
        list.add(new Feature("ownname", "Eigener Name", "Zeigt in der Ansicht von hinten/vorne (F5) deinen Namen über deinem Kopf - so wie andere Spieler ihn sehen.",
                Category.CLIENT, "mod_tag", true, bool("ownNametag"), setBool("ownNametag", false), List.of(), null));
        Setting themeColor = cycle("Farbe", "themeMode", Theme.NAMES);
        list.add(new Feature("theme", "Themefarbe", "Färbt Menüs, HUD und Logo des DeuClients in deiner Farbe.",
                Category.CLIENT, "mod_color-palette", true, null, null, List.of(
                new Setting("Farbe", Kind.CYCLE, Theme.NAMES, 0, Theme.CUSTOM, 1, "", themeColor.get(),
                        v -> { themeColor.set().accept(v); Theme.refresh(); }),
                new Setting("Farbton", Kind.STEP, null, 0, 355, 5, "°",
                        () -> DeuConfig.get().themeHue,
                        v -> { DeuConfig.get().themeHue = v; DeuConfig.save(); Theme.refresh(); },
                        () -> DeuConfig.get().themeMode == Theme.CUSTOM)), null));
        list.add(new Feature("logonames", "Namensicon", "Zeigt das DeuClient-Icon neben den Namen aller Spieler, die den DeuClient nutzen - über dem Kopf und in der Tab-Liste.",
                Category.CLIENT, "mod_emblem", false, bool("logoNames"), setBool("logoNames", false), List.of(), null));
        Field zoomKey = field("zoomKey");
        list.add(new Feature("zoom", "Zoom", "Taste gedrückt halten zum Heranzoomen, das Mausrad zoomt weiter.",
                Category.CLIENT, "mod_zoom", true, bool("zoom"), setBool("zoom", false), List.of(
                cycle("Zoom-Stufe", "zoomLevel", Zoom.LEVEL_NAMES),
                toggle("Weicher Zoom", "zoomSmooth"),
                new Setting("Taste", Kind.CYCLE, ZOOM_KEY_NAMES, 0, ZOOM_KEYS.length - 1, 1, "",
                        () -> { try { int k = zoomKey.getInt(DeuConfig.get()); for (int i = 0; i < ZOOM_KEYS.length; i++) if (ZOOM_KEYS[i] == k) return i; } catch (IllegalAccessException ignored) {} return 0; },
                        v -> { try { zoomKey.setInt(DeuConfig.get(), ZOOM_KEYS[Math.floorMod(v, ZOOM_KEYS.length)]); } catch (IllegalAccessException ignored) {} DeuConfig.save(); })),
                null));
        list.add(new Feature("crosshair", "Fadenkreuz", "Eigenes Fadenkreuz in DeuClient-Farben und verschiedenen Formen.",
                Category.CLIENT, "mod_crosshair", true, bool("customCrosshair"), setBool("customCrosshair", false), List.of(
                cycle("Form", "crosshairStyle", Crosshair.STYLES),
                cycle("Farbe", "crosshairColor", Crosshair.COLOR_NAMES)), p -> new eu.deusmp.deucore.screen.CrosshairEditorScreen(p)));


        // ---- HUD-Anzeigen: jede Anzeige ist ein eigenes Modul mit Schalter
        for (de.erik.glasshud.hud.widgets.Widget w : de.erik.glasshud.hud.widgets.Widgets.ALL) {
            final String wid = w.id;
            List<Setting> st = new ArrayList<>(HudStyle.settings(wid));
            for (de.erik.glasshud.hud.widgets.Widget.Option o : w.options) {
                final String key = o.key();
                final int def = o.def();
                st.add(new Setting(o.label(), Kind.CYCLE, o.values(), 0, o.values().length - 1, 1, "",
                        () -> de.erik.glasshud.config.HudConfig.get().w(wid).opts.getOrDefault(key, def),
                        v -> { de.erik.glasshud.config.HudConfig.get().w(wid).opts.put(key, v); de.erik.glasshud.config.HudConfig.save(); }).in("Inhalt"));
            }
            list.add(new Feature("hud_" + wid, w.name, HUD_DESC.getOrDefault(wid, ""), Category.GLASS,
                    HUD_ICON.getOrDefault(wid, Ui.I_HUD), false,
                    () -> de.erik.glasshud.config.HudConfig.get().w(wid).enabled,
                    on -> { de.erik.glasshud.config.HudConfig.get().w(wid).enabled = on; de.erik.glasshud.config.HudConfig.save(); },
                    st, null));
        }

        // ---- Glass-Mods
        for (GlassMod m : GlassMods.ALL) {
            if (!m.installed() || m.modId().equals("glasshud")) continue; // HUD: jede Anzeige hat ihre eigene Karte
            boolean scout = m.modId().equals("glassscout");
            boolean discord = m.modId().equals("discordglass");
            // Scout und Discord: dieselbe "Darstellung" wie die HUD-Elemente, alles Weitere unter "Inhalt"
            List<Setting> settings = new ArrayList<>(HudStyle.settings(scout ? HudStyle.SCOUT : HudStyle.DISCORD));
            GlassMods.Option master = null;
            for (GlassMods.Option o : m.options()) {
                if (o.field().equals("hudEnabled")) master = o;
                Kind k = switch (o.kind()) { case TOGGLE -> Kind.TOGGLE; case CYCLE -> Kind.CYCLE; case PERCENT -> Kind.STEP; };
                settings.add(new Setting(o.label(), k, o.names(), o.min(), o.max(), o.step(), k == Kind.STEP ? " %" : "",
                        () -> m.get(o), v -> m.set(o, v)).in("Inhalt"));
            }
            final GlassMods.Option mo = master;
            final String flag = m.modId().equals("glassscout") ? "scoutEnabled" : m.modId().equals("discordglass") ? "discordEnabled" : null;
            String icon = switch (m.modId()) {
                case "glasshud" -> Ui.I_HUD;
                case "discordglass" -> Ui.I_CHAT;
                default -> "mod_eye";
            };
            BooleanSupplier isOn = flag != null ? bool(flag) : mo == null ? null : () -> m.get(mo) == 1;
            Consumer<Boolean> setOn = flag != null ? setBool(flag, false) : mo == null ? null : v -> m.set(mo, v ? 1 : 0);
            if (discord) { // der Kartenschalter gilt auch fuer das per Taste ausblendbare Call-HUD
                isOn = () -> DeuConfig.get().discordEnabled && de.erik.discordglass.config.GlassConfig.get().hudEnabled;
                setOn = v -> {
                    setBool("discordEnabled", false).accept(v);
                    de.erik.discordglass.config.GlassConfig.get().hudEnabled = v;
                    de.erik.discordglass.config.GlassConfig.save();
                };
            }
            // der Scout hat keine Taste, sein Befehl steht in der Beschreibung
            String description = scout ? m.description() : m.description() + " Taste: " + m.hotkey();
            list.add(new Feature(m.modId(), m.name(), description, Category.GLASS, icon, false,
                    isOn, setOn, settings, m.screenClass() == null ? null : m::createScreen));
        }
        return list;
    }
}
