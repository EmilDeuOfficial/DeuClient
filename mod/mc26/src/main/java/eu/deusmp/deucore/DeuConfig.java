package eu.deusmp.deucore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Einstellungen von DeuCore (config/deucore.json) - im Mod-Menü (Rechts-Shift) änderbar. */
public class DeuConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("deucore.json");
    /** Alter Dateiname vor der Umbenennung - nur noch Migrationsquelle, wird nie geschrieben */
    private static final String LEGACY_FILE = "divinecore.json";
    private static DeuConfig instance;

    // ---- Deu-Optik
    public boolean customTitleScreen = true;
    public boolean customLoadingScreen = true;
    public boolean customButtons = true;
    public boolean customBackground = true;
    public boolean particles = true;
    public boolean customPauseMenu = true;
    public boolean customWindowTitle = true;

    // ---- Client-Funktionen
    /** Deu-Zeile mit Spielername oben im F3-Bildschirm */
    public boolean f3Name = true;
    /** Eigenes Namensschild über dem Kopf in der Ansicht von hinten/vorne (F5) */
    public boolean ownNametag = true;
    /** Hintergrund der Menüs: true = verschwommenes Welt-Panorama (clean), false = Deu-Animation */
    public boolean panoramaBackground = true;
    /** Deu-Logo neben den Namen von Deu-Spielern (Namensschild + Tab-Liste) */
    public boolean logoNames = true;
    /** Zoom (gedrückt halten) */
    public boolean zoom = true;
    public int zoomKey = InputConstants.KEY_C;
    /** Zoom-Stufe: 0 = 2×, 1 = 4×, 2 = 6×, 3 = 10× */
    public int zoomLevel = 1;
    public boolean zoomSmooth = true;
    /** Eigenes Fadenkreuz */
    public boolean customCrosshair = true;
    public int crosshairStyle = 0;
    public int crosshairColor = 3;
    /** Eigene Form: 11x11 Zeichen aus 0/1 (Zeile fuer Zeile); leer = nichts gezeichnet */
    public String crosshairPixels = "";
    /** Eigene Farbe (RGB), wird bei crosshairColor = 6 benutzt */
    public int crosshairCustomColor = 0x2EFF7A;

    // ---- Cosmetics (für alle Deu-Spieler sichtbar, siehe DeuNetwork)
    public String cape = "";
    public String wings = "";
    /** Beim Tragen einer Elytra: true = die gewaehlten Fluegel statt der Elytra, false = Elytra mit Cape-Textur */
    public boolean elytraWings = false;
    /** Im Kosmetik-Menue eingeklappte Kollektionen (Ids) */
    public java.util.List<String> collapsedCollections = new java.util.ArrayList<>();
    public boolean showCosmetics = true;
    /** Scout (Modul) an/aus */
    public boolean scoutEnabled = true;
    /** Discord (Modul) an/aus */
    public boolean discordEnabled = true;
    /** Gruene Akzentfarbe der Module wurde einmalig gesetzt */
    public boolean moduleThemeApplied = false;
    public boolean wingsAnimated = true;
    /** Fluegelgroesse (1.0 = Modellgroesse), einstellbar im Kosmetik-Menue */
    public float wingScale = 1.0f;
    /** Themefarbe: 0 = wie Launcher, sonst Vorgabe aus Theme.NAMES; Farbton fuer "Eigene" */
    public int themeMode = 0;
    public int themeHue = 142;

    // ---- Mod-Menü
    public List<String> favorites = new ArrayList<>();

    public static DeuConfig get() {
        if (instance == null) load();
        return instance;
    }

    /** Uebernimmt eine alte divinecore.json einmalig als deucore.json (kopieren, nie ueberschreiben). */
    private static void migrateLegacyFile() {
        try {
            Path legacy = FILE.resolveSibling(LEGACY_FILE);
            if (!Files.exists(FILE) && Files.exists(legacy)) {
                Files.copy(legacy, FILE);
                DeuCore.LOGGER.info("Config {} nach {} uebernommen", LEGACY_FILE, FILE.getFileName());
            }
        } catch (Exception e) {
            DeuCore.LOGGER.warn("{} konnte nicht uebernommen werden", LEGACY_FILE, e);
        }
    }

    public static void load() {
        migrateLegacyFile();
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    instance = GSON.fromJson(r, DeuConfig.class);
                }
            }
        } catch (Exception e) {
            DeuCore.LOGGER.warn("deucore.json konnte nicht gelesen werden", e);
        }
        if (instance == null) instance = new DeuConfig();
        if (instance.favorites == null) instance.favorites = new ArrayList<>();
        if (instance.cape == null) instance.cape = "";
        if (instance.wings == null) instance.wings = "";
        if (!(instance.wingScale >= 0.3f && instance.wingScale <= 2.0f)) instance.wingScale = 1.0f;
        // entfernte Designs (alte Namen aus bestehenden Configs: "divine", "divinesmp") auf "nichts" zuruecksetzen
        if (instance.cape.equals("divine") || instance.cape.equals("divinesmp")) instance.cape = "";
        if (instance.wings.equals("divine")) instance.wings = "";
        // diese Funktionen sind fest eingebaut (keine Schalter mehr im Mod-Menue)
        instance.customTitleScreen = true;
        instance.customLoadingScreen = true;
        instance.customButtons = true;
        instance.customBackground = true;
        instance.customPauseMenu = true;
        instance.customWindowTitle = true;
        save();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            DeuCore.LOGGER.warn("deucore.json konnte nicht gespeichert werden", e);
        }
    }
}
