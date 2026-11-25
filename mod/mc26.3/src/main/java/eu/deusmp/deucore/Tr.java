package eu.deusmp.deucore;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uebersetzung der Mod-Texte: Die Texte im Code sind Deutsch und dienen als Schluessel.
 * Ist Minecraft nicht auf Deutsch gestellt, werden sie ins Englische umgesetzt.
 */
public final class Tr {
    private Tr() {}

    private static final Map<String, String> EN = new HashMap<>();
    private static final Pattern RULE_VISIBLE = Pattern.compile("sichtbar · (\\d+) online");
    private static final Pattern RULE_HOTKEY = Pattern.compile("(.*) Taste: (.*)");
    private static final Pattern RULE_VERSION = Pattern.compile("DeuClient (.*) · Rechts-Shift: Mod-Menü");

    private static void p(String de, String en) {
        EN.put(de, en);
    }

    static {
        // Kategorien
        p("Alle", "All"); p("Favoriten", "Favorites"); p("Optik", "Visuals"); p("Client", "Client");
        p("Kosmetik", "Cosmetics"); p("Module", "Modules");
        // Kopf / Tabs / Allgemein
        p("MOD-MENÜ", "MOD MENU"); p("KOSMETIK", "COSMETICS"); p("Mod-Menü", "Mod menu");
        p("Suchen...", "Search..."); p("Suche", "Search"); p("Zurück", "Back");
        p("Keine Funktionen gefunden.", "No features found.");
        p("Aktiviert", "Enabled"); p("Deaktiviert", "Disabled"); p("Immer an", "Always on");
        p("§aAn", "§aOn"); p("§cAus", "§cOff");
        p("SPIEL BEENDEN", "QUIT GAME");
        p("Capes", "Capes"); p("Flügel", "Wings"); p("Keine", "None"); p("Eigenes", "Custom");
        p("Kosmetik öffnen", "Open cosmetics"); p("Eigenes Menü öffnen", "Open custom menu");
        p("Alle Einstellungen findest du im eigenen Menü.", "You can find all settings in the custom menu.");
        p("Keine weiteren Einstellungen.", "No further settings.");
        p("3D-Vorschau im Spiel", "3D preview in game"); p("Für alle DeuClient-Spieler", "For all DeuClient players");
        p("GALAXIE-", "GALAXY"); p("FLÜGEL", "WINGS"); p("Capes & Flügel", "Capes & wings"); p("für alle sichtbar", "visible to all");
        // Kosmetik-Namen
        p("Sternennacht", "Starry night"); p("Flamme", "Flame"); p("Eiskristall", "Ice crystal"); p("Smaragd", "Emerald");
        p("Engel", "Angel"); p("Dämon", "Demon"); p("Eis", "Ice"); p("Phönix", "Phoenix"); p("Schatten", "Shadow");
        // Funktionen
        p("F3-Name", "F3 name");
        p("Zeigt \"DeuClient\" und deinen Spielernamen oben im F3-Bildschirm.", "Shows \"DeuClient\" and your player name at the top of the F3 screen.");
        p("Eigener Name", "Own name");
        p("Zeigt in der Ansicht von hinten/vorne (F5) deinen Namen über deinem Kopf - so wie andere Spieler ihn sehen.",
                "Shows your name above your head in the back/front view (F5) - just like other players see it.");
        p("Zoom", "Zoom"); p("Taste gedrückt halten zum Heranzoomen - wie ein Fernglas.", "Hold the key to zoom in - like binoculars.");
        p("Zoom-Stufe", "Zoom level"); p("Weicher Zoom", "Smooth zoom"); p("Taste", "Key");
        p("Fadenkreuz", "Crosshair"); p("Eigenes Fadenkreuz in DeuClient-Farben und verschiedenen Formen.", "Custom crosshair in DeuClient colors and different shapes.");
        p("Form", "Shape"); p("Farbe", "Color");
        p("Kreuz", "Cross"); p("Punkt", "Dot"); p("Kreuz + Punkt", "Cross + dot"); p("Kreis", "Circle"); p("Pfeil", "Arrow");
        p("Weiß", "White"); p("Violett", "Violet"); p("Grün", "Green"); p("Rot", "Red"); p("Rosa", "Pink"); p("Bernstein", "Amber");
        p("Hintergrund", "Background");
        p("Eigener Hintergrund in allen Menüs: verschwommenes Welt-Panorama oder DeuClient-Animation.", "Custom background in all menus: blurred world panorama or DeuClient animation.");
        p("Welt-Panorama (aus = DeuClient-Animation)", "World panorama (off = DeuClient animation)");
        p("Goldpartikel", "Gold particles");
        p("Pause-Menü", "Pause menu"); p("DeuClient-Overlay, Schriftzug und Mod-Menü-Button im Pause-Menü.", "DeuClient overlay, logo and mod menu button in the pause menu.");
        p("Fenstertitel", "Window title"); p("\"DeuClient - Minecraft <Version>\" als Fenstertitel.", "\"DeuClient - Minecraft <version>\" as the window title.");
        // Glass-Mods
        p("Frei verschiebbares HUD: Koordinaten, FPS, Ping, Rüstung, Trank-Timer, Tasten, Kompass.", "Freely movable HUD: coordinates, FPS, ping, armor, potion timers, keys, compass.");
        p("Zeigt deinen Discord-Call, Mitglieder, Mute/Deafen und Benachrichtigungen ingame.", "Shows your Discord call, members, mute/deafen and notifications in game.");
        p("Zeigt bei /invsee sofort Kristalle, Totems, XP-Flaschen und die schwächste Rüstung des Gegners.", "Shows crystals, totems, XP bottles and the weakest armor of the opponent right away on /invsee.");
        p("HUD-Editor öffnen", "Open HUD editor"); p("Discord-Menü öffnen", "Open Discord menu");
        p("Akzentfarbe", "Accent color"); p("Deckkraft", "Opacity"); p("Vanilla-Effektanzeige ausblenden", "Hide vanilla effect display");
        p("Call-HUD anzeigen", "Show call HUD"); p("HUD-Position", "HUD position"); p("Nur sprechende Mitglieder", "Only speaking members");
        p("Benachrichtigungen", "Notifications"); p("HUD-Deckkraft", "HUD opacity"); p("Automatisch verbinden", "Connect automatically");
        p("Anzeigen bei", "Show on"); p("Nur /invsee", "Only /invsee"); p("Jedem Container", "Any container");
        p("Extras (Goldäpfel, Perlen, Obsidian …)", "Extras (golden apples, pearls, obsidian ...)");

        // HUD-Anzeigen
        p("Größe", "Size"); p("Koordinaten", "Coordinates"); p("Kompass", "Compass"); p("Effekte", "Effects"); p("Tempo", "Speed");
        p("Rüstung", "Armor"); p("Uhrzeit", "Clock"); p("Tasten", "Keys");
        p("Zeigt deine Koordinaten und Blickrichtung.", "Shows your coordinates and facing direction.");
        p("Zeigt die Bilder pro Sekunde mit Diagramm.", "Shows frames per second with a graph.");
        p("Zeigt deinen Ping zum Server.", "Shows your ping to the server.");
        p("Zeigt deine Geschwindigkeit.", "Shows your speed.");
        p("Kompassleiste mit Himmelsrichtungen.", "Compass bar with cardinal directions.");
        p("Zeigt die Uhrzeit und die Spielzeit.", "Shows the time and the in-game time.");
        p("Zeigt aktive Effekte mit Zeitbalken.", "Shows active effects with time bars.");
        p("Zeigt deine Rüstung und das Werkzeug in der Hand mit Haltbarkeit.", "Shows your armor and the tool in your hand with durability.");
        p("Zeigt gedrückte Tasten und Klicks pro Sekunde.", "Shows pressed keys and clicks per second.");
        p("Gemeinsame Einstellungen aller HUD-Anzeigen: Farbe, Deckkraft und der Editor zum Verschieben.", "Shared settings of all HUD displays: color, opacity and the editor to move them.");
        p("Gradzahl", "Degrees"); p("Diagramm", "Graph"); p("Zeitbalken", "Time bars"); p("Einheit", "Unit"); p("Blöcke/s", "Blocks/s");
        p("Anzeige", "Display"); p("Prozent", "Percent"); p("Haltbarkeit", "Durability"); p("Aus", "Off"); p("An", "On");
        p("Werkzeug", "Tool"); p("Spielzeit", "In-game time"); p("Sekunden", "Seconds"); p("Nachkommastellen", "Decimals");
        p("Eine", "One"); p("Blickrichtung", "Facing"); p("Maustasten", "Mouse buttons"); p("Leertaste", "Space bar"); p("Klicks/Sek.", "Clicks/sec");
        // Discord / Scout
        p("Kristalle", "Crystals"); p("Totems", "Totems"); p("XP-Flaschen", "XP bottles"); p("RÜSTUNG", "ARMOR");
        p("Verbinden", "Connect"); p("Trennen", "Disconnect"); p("Stumm", "Muted"); p("Mikrofon", "Microphone"); p("Taub", "Deafened");
        p("Ton", "Sound"); p("Auflegen", "Hang up"); p("Aktualisieren", "Refresh"); p("IM CALL", "IN CALL");
        p("Kein Sprachkanal", "No voice channel"); p("Lade Server…", "Loading servers…"); p("Lade Kanäle…", "Loading channels…");
        p("Keine Sprachkanäle", "No voice channels"); p("Tritt einem Kanal bei, um ihn hier zu sehen.", "Join a channel to see it here.");
        p("Beitreten", "Join"); p("(du)", "(you)"); p("weitere", "more");
        p("Zurücksetzen", "Reset"); p("Anzeigen per Maus anordnen", "Arrange displays with the mouse");
        p("um sie anzupassen.", "to adjust them.");
        p("Namensicon", "Name icon");
        p("Zeigt das DeuClient-Icon neben den Namen aller Spieler, die den DeuClient nutzen - über dem Kopf und in der Tab-Liste.", "Shows the DeuClient icon next to the names of all players using the DeuClient - above the head and in the tab list.");
        p("Eigene", "Custom"); p("Leeren", "Clear"); p("Kreuz als Vorlage", "Cross as template"); p("Fertig", "Done");
        p("Form zeichnen", "Draw shape"); p("Fadenkreuz zeichnen", "Draw crosshair"); p("Klicken und ziehen zum Malen", "Click and drag to paint"); p("Vorschau", "Preview");
        p("Oben links", "Top left"); p("Oben rechts", "Top right"); p("Unten links", "Bottom left"); p("Unten rechts", "Bottom right");
    }

    /** true, wenn Minecraft auf Deutsch steht; sonst werden die Texte englisch angezeigt */
    public static boolean german() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.languageCode == null) return true;
        return mc.options.languageCode.toLowerCase(Locale.ROOT).startsWith("de");
    }

    /** Uebersetzt einen deutschen Text passend zur Minecraft-Sprache (unbekannte Texte bleiben unveraendert) */
    public static String t(String de) {
        if (de == null || de.isEmpty() || german()) return de;
        String en = EN.get(de);
        if (en != null) return en;
        Matcher m = RULE_VISIBLE.matcher(de);
        if (m.matches()) return "visible · " + m.group(1) + " online";
        m = RULE_VERSION.matcher(de);
        if (m.matches()) return "DeuClient " + m.group(1) + " · Right Shift: Mod menu";
        m = RULE_HOTKEY.matcher(de);
        if (m.matches()) return t(m.group(1)) + " Key: " + m.group(2);
        return de;
    }
}
