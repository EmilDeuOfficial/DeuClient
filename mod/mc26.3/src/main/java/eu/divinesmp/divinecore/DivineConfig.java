package eu.divinesmp.divinecore;

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

/** Einstellungen von DivineCore (config/divinecore.json) – im Mod-Menü (Rechts-Shift) änderbar. */
public class DivineConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("divinecore.json");
    private static DivineConfig instance;

    // ---- Divine-Optik
    public boolean customTitleScreen = true;
    public boolean customLoadingScreen = true;
    public boolean customButtons = true;
    public boolean customBackground = true;
    public boolean particles = true;
    public boolean customPauseMenu = true;
    public boolean customWindowTitle = true;

    // ---- Client-Funktionen
    /** Divine-Zeile mit Spielername oben im F3-Bildschirm */
    public boolean f3Name = true;
    /** Eigenes Namensschild über dem Kopf in der Ansicht von hinten/vorne (F5) */
    public boolean ownNametag = true;
    /** Hintergrund der Menüs: true = verschwommenes Welt-Panorama (clean), false = Divine-Animation */
    public boolean panoramaBackground = true;
    /** Divine-Logo neben den Namen von Divine-Spielern (Namensschild + Tab-Liste) */
    public boolean logoNames = true;
    /** Zoom (gedrückt halten) */
    public boolean zoom = true;
    public int zoomKey = InputConstants.KEY_C;
    /** Zoom-Stufe: 0 = 2×, 1 = 4×, 2 = 6×, 3 = 10× */
    public int zoomLevel = 1;
    public boolean zoomSmooth = true;
    /** Eigene GUI-Skalierung (auch 2,5); Standard 2,5. false = Einstellung aus den Videoeinstellungen */
    public boolean customGuiScale = true;
    /** Index in GUI_SCALE_VALUES (3 = 2,5) */
    public int guiScale = 3;
    public static final String[] GUI_SCALE_NAMES = {"Auto", "1", "2", "2,5", "3", "4"};
    private static final double[] GUI_SCALE_VALUES = {0, 1, 2, 2.5, 3, 4};

    /** Gewuenschte GUI-Skalierung; 0 = Vanilla-Verhalten */
    public static double guiScaleWanted() {
        // vor dem Laden der Config (Fenster wird sehr frueh erzeugt) gilt das Vanilla-Verhalten
        if (instance == null) return 0;
        DivineConfig c = instance;
        if (!c.customGuiScale) return 0;
        return GUI_SCALE_VALUES[Math.floorMod(c.guiScale, GUI_SCALE_VALUES.length)];
    }

    /** Eigenes Fadenkreuz */
    public boolean customCrosshair = false;
    public int crosshairStyle = 0;
    public int crosshairColor = 1;

    // ---- Cosmetics (für alle Divine-Spieler sichtbar, siehe DivineNetwork)
    public String cape = "";
    public String wings = "";
    public boolean showCosmetics = true;
    public boolean wingsAnimated = true;

    // ---- Mod-Menü
    public List<String> favorites = new ArrayList<>();

    public static DivineConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    instance = GSON.fromJson(r, DivineConfig.class);
                }
            }
        } catch (Exception e) {
            DivineCore.LOGGER.warn("divinecore.json konnte nicht gelesen werden", e);
        }
        if (instance == null) instance = new DivineConfig();
        if (instance.favorites == null) instance.favorites = new ArrayList<>();
        if (instance.cape == null) instance.cape = "";
        if (instance.wings == null) instance.wings = "";
        // entfernte Designs (Divine, DeuClient-Cape) auf "nichts" zuruecksetzen
        if (instance.cape.equals("divine") || instance.cape.equals("divinesmp")) instance.cape = "";
        if (instance.wings.equals("divine")) instance.wings = "";
        save();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            DivineCore.LOGGER.warn("divinecore.json konnte nicht gespeichert werden", e);
        }
    }
}
