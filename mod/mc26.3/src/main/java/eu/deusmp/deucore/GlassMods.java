package eu.deusmp.deucore;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Anbindung der Module (HUD, Discord, Scout) an das DeuClient-Menü. Das HUD ist fest in DeuCore enthalten.
 *
 * Alle Zugriffe laufen über Reflection auf die öffentlichen Config-Klassen der Mods
 * (statisches get()/save()). Fehlt eine Mod, wird sie im Menü einfach ausgeblendet -
 * DeuCore hat keine harte Abhängigkeit darauf.
 */
public final class GlassMods {
    private GlassMods() {}

    public enum Kind { TOGGLE, CYCLE, PERCENT }

    /** Eine einstellbare Option = öffentliches Feld in der Config-Klasse der Mod */
    public record Option(String field, String label, Kind kind, String[] names, int min, int max, int step) {
        static Option toggle(String field, String label) { return new Option(field, label, Kind.TOGGLE, null, 0, 1, 1); }
        static Option cycle(String field, String label, String... names) { return new Option(field, label, Kind.CYCLE, names, 0, names.length - 1, 1); }
        static Option percent(String field, String label, int min, int max) { return new Option(field, label, Kind.PERCENT, null, min, max, 5); }
    }

    /** Beschreibung einer Glass-Mod */
    public record GlassMod(String modId, String name, String description, String hotkey,
                           String configClass, String screenClass, String screenButton, List<Option> options) {

        public boolean installed() {
            return true; // die Module sind fest in DeuCore eingebaut
        }

        private Object config() throws ReflectiveOperationException {
            return Class.forName(configClass).getMethod("get").invoke(null);
        }

        public void save() {
            try {
                Class.forName(configClass).getMethod("save").invoke(null);
            } catch (ReflectiveOperationException e) {
                DeuCore.LOGGER.warn("{}: Speichern fehlgeschlagen", name, e);
            }
        }

        /** Aktueller Wert einer Option als int (boolean → 0/1); -1 bei Fehler */
        public int get(Option o) {
            try {
                Field f = config().getClass().getField(o.field());
                Object v = f.get(config());
                if (v instanceof Boolean b) return b ? 1 : 0;
                if (v instanceof Number n) return n.intValue();
            } catch (ReflectiveOperationException e) {
                DeuCore.LOGGER.debug("{}.{} nicht lesbar", name, o.field(), e);
            }
            return -1;
        }

        public void set(Option o, int value) {
            try {
                Object cfg = config();
                Field f = cfg.getClass().getField(o.field());
                if (f.getType() == boolean.class) f.setBoolean(cfg, value != 0);
                else if (f.getType() == int.class) f.setInt(cfg, value);
                save();
            } catch (ReflectiveOperationException e) {
                DeuCore.LOGGER.warn("{}.{} nicht setzbar", name, o.field(), e);
            }
        }

        /** Öffnet den eigenen Einstellungs-Screen der Mod (Konstruktor mit Parent-Screen) */
        public Screen createScreen(Screen parent) {
            if (screenClass == null) return null;
            try {
                return (Screen) Class.forName(screenClass).getConstructor(Screen.class).newInstance(parent);
            } catch (ReflectiveOperationException e) {
                DeuCore.LOGGER.warn("{}: Menü konnte nicht geöffnet werden", name, e);
                return null;
            }
        }
    }

    private static final String[] CORNERS = {"Oben links", "Oben rechts", "Unten links", "Unten rechts"};

    public static final List<GlassMod> ALL = List.of(
            new GlassMod("glasshud", "HUD",
                    "Gemeinsame Einstellungen aller HUD-Anzeigen: Farbe, Deckkraft und der Editor zum Verschieben.",
                    "F8",
                    "de.erik.glasshud.config.HudConfig", "de.erik.glasshud.gui.EditorScreen", "HUD-Editor öffnen",
                    List.of(
                            Option.cycle("accent", "Akzentfarbe", "Violett", "Cyan", "Smaragd", "Rosa", "Bernstein", "Weiß"),
                            Option.percent("opacity", "Deckkraft", 20, 100),
                            Option.toggle("hideVanillaEffects", "Vanilla-Effektanzeige ausblenden"))),
            new GlassMod("discordglass", "Discord",
                    "Zeigt deinen Discord-Call, Mitglieder, Mute/Deafen und Benachrichtigungen ingame.",
                    "Strg + Y",
                    "de.erik.discordglass.config.GlassConfig", "de.erik.discordglass.gui.GlassScreen", "Discord-Menü öffnen",
                    List.of(
                            Option.toggle("hudEnabled", "Call-HUD anzeigen"),
                            Option.cycle("hudCorner", "HUD-Position", CORNERS),
                            Option.toggle("hudOnlySpeaking", "Nur sprechende Mitglieder"),
                            Option.toggle("notifications", "Benachrichtigungen"),
                            Option.percent("hudOpacity", "HUD-Deckkraft", 20, 100),
                            Option.toggle("autoConnect", "Automatisch verbinden"),
                            Option.toggle("richPresence", "Rich Presence"))),
            new GlassMod("glassscout", "Scout",
                    "Zeigt bei /invsee sofort Kristalle, Totems, XP-Flaschen und die schwächste Rüstung des Gegners.",
                    "/invsee",
                    "de.erik.glassscout.config.ScoutConfig", null, null,
                    List.of(
                            Option.cycle("mode", "Anzeigen bei", "Nur /invsee", "Jedem Container"),
                            Option.toggle("showExtras", "Extras (Goldäpfel, Perlen, Obsidian …)"),
                            Option.cycle("accent", "Akzentfarbe", "Rot", "Cyan", "Violett", "Smaragd", "Bernstein", "Weiß"),
                            Option.percent("opacity", "Deckkraft", 20, 100)))
    );
}
