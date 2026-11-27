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
        p("Dunkel", "Dark"); p("Schwarz", "Black"); p("Einstellungen", "Settings"); p("Größe zurücksetzen", "Reset size");
        p("Ausblenden", "Hide"); p("Anzeige hinzufügen", "Add element"); p("Alle Anzeigen sind aktiv", "All elements are active");
        p("Minecraft-Optionen", "Minecraft options"); p("Alles zurücksetzen", "Reset all"); p("Rechtsklick öffnet das Menü", "Right click opens the menu");
        p("Raster ausblenden", "Hide grid"); p("Raster anzeigen", "Show grid"); p("Nichts anlegen", "Wear nothing"); p("Eigene", "Own");
        p("Taste gedrückt halten zum Heranzoomen, das Mausrad zoomt weiter.", "Hold the key to zoom, the mouse wheel zooms further.");
        p("Mythos-Kollektion", "Mythos Collection"); p("DEIN SPIELER", "YOUR PLAYER"); p("Elytra: Flügel", "Elytra: Wings"); p("Elytra: Cape", "Elytra: Cape");
        p("Mit angelegter Elytra: Elytra in Cape-Textur oder deine Flügel mit Fluganimationen.", "With an elytra equipped: elytra in the cape texture, or your wings with flight animations.");
        p("Ziehen zum Drehen", "Drag to rotate");
        p("Rückansicht", "Back view"); p("Optionen", "Options"); p("HUD bearbeiten", "Edit HUD"); p("Flügelgröße", "Wing size");
        p("Sternennacht", "Starry night"); p("Flamme", "Flame"); p("Eiskristall", "Ice crystal"); p("Smaragd", "Emerald");
        p("Engel", "Angel"); p("Dämon", "Demon"); p("Eis", "Ice"); p("Phönix", "Phoenix"); p("Schatten", "Shadow");
        // Funktionen
        p("F3-Name", "F3 name"); p("Spieler", "Player");
        p("HUD-Transparenz", "HUD transparency");
        p("Suchen", "Search");
        p("Discord ließ sich nicht öffnen", "Discord could not be opened");
        p("Text", "Text");
        p("Direktnachrichten", "Direct messages");
        p("Server", "Servers");
        p("Keine Textkanäle", "No text channels");
        p("In Discord schreiben", "Write in Discord");
        p("Anrufen", "Call");
        p("Nachrichten werden geladen…", "Loading messages…");
        p("Keine Nachrichten", "No messages");
        p("Nachrichten nicht lesbar", "Messages cannot be read");
        p("Direktnachrichten erscheinen hier, sobald eine neue eingeht.", "Direct messages appear here as soon as a new one arrives.");
        p("Anhang", "Attachment");
        p("Rolle", "role");
        p("Kanal", "channel");
        p("jemand", "someone");
        p("Eigene App aktiv, aber keine App-ID eingetragen", "Own app is on, but no app ID is entered");
        p("Öffne Discord, die Mod verbindet sich automatisch.", "Open Discord, the mod connects automatically.");
        p("Discord hat die Verbindung geschlossen", "Discord closed the connection");
        p("Verbindung geschlossen", "Connection closed");
        p("Discord läuft nicht (keine IPC-Pipe gefunden)", "Discord is not running (no IPC pipe found)");
        p("Senden fehlgeschlagen", "Sending failed");
        p("Beitreten fehlgeschlagen", "Joining failed");
        p("Client-Secret fehlt (Einstellungen, Eigene Discord-App)", "Client secret is missing (Settings, Own Discord app)");
        p("Ein Fenster in Discord wartet auf deine Bestätigung", "A window in Discord is waiting for your confirmation");
        p("Autorisierung abgelehnt", "Authorization declined");
        p("Anmeldung fehlgeschlagen", "Sign-in failed");
        p("Versuche es erneut oder nutze eine eigene App.", "Try again or use your own app.");
        p("Token-Tausch fehlgeschlagen", "Token exchange failed");
        p("Direktanruf", "Direct call");
        p("Im Hauptmenü", "In the main menu");
        p("Spielt Einzelspieler", "Playing singleplayer");
        p("Spielt Mehrspieler", "Playing multiplayer");
        p("Auf einem Server", "On a server");
        p("Discord: Taub geschaltet", "Discord: Deafened");
        p("Discord: Stumm geschaltet", "Discord: Muted");
        p("Strg", "Ctrl"); p("Nicht belegt", "Not bound");
        p("Call", "Call");
        p("du", "you");
        p("Nicht verbunden", "Not connected");
        p("Bestätige in Discord", "Confirm in Discord");
        p("Verbinde mit Discord…", "Connecting to Discord…");
        p("Verbindung fehlgeschlagen", "Connection failed");
        p("Warte auf Discord…", "Waiting for Discord…");
        p("Klicke im Discord-Fenster auf \"Autorisieren\".", "Click \"Authorize\" in the Discord window.");
        p("Öffne die Discord-App auf diesem PC, mehr ist nicht nötig.", "Open the Discord app on this PC, nothing else is needed.");
        p("Getrennt", "Disconnected");
        p("Warte auf Discord", "Waiting for Discord");
        p("Verbinde…", "Connecting…");
        p("Bitte in Discord bestätigen", "Please confirm in Discord");
        p("Verbunden", "Connected");
        p("Fehler", "Error");
        p("Kein Treffer", "No match");
        p("Server suchen", "Search servers");
        p("Wähle links einen Kanal.", "Pick a channel on the left.");
        p("Freunde", "Friends");
        p("Noch keine. Klicke bei Personen im Call oder Chat auf den Stern.", "None yet. Click the star next to people in a call or chat.");
        p("Als Freund merken", "Add to friends");
        p("Aus Freunden entfernen", "Remove from friends");
        p("Profil in Discord öffnen", "Open profile in Discord");
        p("Freunde und Server stehen links. Zum Schreiben öffnet sich die Discord-App.", "Friends and servers are on the left. Writing opens the Discord app.");
        p("Der Chat erscheint hier, sobald eine Nachricht oder ein Anruf von dieser Person kommt.", "The chat shows up here once a message or call from this person arrives.");
        p("Overlay", "Overlay");
        p("Position", "Position");
        p("Größe und Hintergrund", "Size and background");
        p("Verbindung", "Connection");
        p("Eigene Discord-App", "Own Discord app");
        p("Redirect: http://localhost", "Redirect: http://localhost");
        p("Anmeldung zurücksetzen", "Reset login");
        p("Menü öffnen", "Open menu");
        p("Stummschalten", "Mute");
        p("Taubschalten", "Deafen");
        p("HUD ein/aus", "Toggle HUD");
        p("Call verlassen", "Leave call");
        p("Taste drücken…", "Press a key…");
        p("Rechtsklick entfernt eine Taste.", "Right click removes a key.");
        p("Application ID", "Application ID");
        p("Client Secret", "Client Secret");
        p("Öffnest du ein fremdes Inventar (/invsee), zeigt eine Karte daneben Kristalle, Totems, XP-Flaschen, Extras und das schwächste Rüstungsteil.",
                "When you open another inventory (/invsee), a card next to it shows crystals, totems, XP bottles, extras and the weakest armor piece.");
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
        p("Hintergrund", "Background"); p("GUI-Größe", "GUI size"); p("Feinschritt", "Fine step");
        p("Stellt die Größe der Oberfläche stufenlos ein, zum Beispiel 2,53x.", "Sets the interface size in decimals, for example 2.53x.");
        p("HUD bearbeiten", "Edit HUD"); p("Mit Rand", "With border"); p("Nur Schwarz", "Black only"); p("Transparenz", "Transparency");
        p("Position verschieben", "Move position"); p("Fertig", "Done"); p("Zurücksetzen", "Reset"); p("KATEGORIEN", "CATEGORIES");
        p("Ziehen zum Verschieben, Mausrad ändert die Größe", "Drag to move, mouse wheel changes the size");
        p("Darstellung", "Appearance"); p("Inhalt", "Content"); p("Auf alle übertragen", "Apply to all"); p("Übertragen", "Applied");
        p("Themefarbe", "Theme color"); p("Farbton", "Hue"); p("Wie Launcher", "Same as launcher"); p("Blau", "Blue"); p("Violett", "Purple");
        p("Rot", "Red"); p("Orange", "Orange"); p("Gelb", "Yellow"); p("Pink", "Pink"); p("Türkis", "Teal"); p("Weiß", "White"); p("Eigene", "Custom");
        p("Färbt Menüs, HUD und Logo des DeuClients in deiner Farbe.", "Colors the DeuClient menus, HUD and logo in your color.");
        p("Kristalle", "Crystals"); p("Totems", "Totems"); p("XP-Flaschen", "XP bottles"); p("RÜSTUNG", "ARMOR");
        p("Keine Rüstung gefunden", "No armor found"); p("schwächstes Teil markiert", "weakest piece marked");
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
        if (m.matches()) return t(m.group(1)) + " Key: " + m.group(2).replace("Strg", "Ctrl");
        return de;
    }
}
