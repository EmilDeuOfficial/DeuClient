package de.erik.glassscout.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public class ScoutConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("glassscout.json");
   private static ScoutConfig instance;
   public static final String[] ACCENT_NAMES = new String[]{"rot", "cyan", "violett", "smaragd", "bernstein", "weiß"};
   public static final int[] ACCENT_COLORS = new int[]{-41874, -11546113, -7635713, -13901702, -18355, -1447182};
   public int mode = 0;
   public boolean showExtras = true;
   public int accent = 3;
   public int opacity = 92;
   /** Darstellung wie bei den HUD-Elementen */
   public float scale = 1.0F;
   public int bgStyle = 1;
   public int bgTransparency = 10;
   public int styleVersion = 0;
   public List<String> commands = new ArrayList<>(List.of("invsee", "openinv", "oi", "inv", "invs", "inventorysee", "einsehen"));

   public int accentColor() {
      return 0xFF2EFF7A; // Themefarbe des Clients (wird beim Zeichnen umgefaerbt)
   }

   public static ScoutConfig get() {
      if (instance == null) {
         load();
      }

      return instance;
   }

   public static void load() {
      try {
         if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
               instance = (ScoutConfig)GSON.fromJson(r, ScoutConfig.class);
            }
         }
      } catch (Exception var5) {
         System.err.println("[GlassScout] Konnte Config nicht laden: " + var5);
      }

      if (instance == null) {
         instance = new ScoutConfig();
      }

      if (instance.commands == null) {
         instance.commands = new ArrayList<>(List.of("invsee"));
      }

      if (instance.styleVersion < 1) {
         if (instance.bgTransparency == 0) {
            instance.bgTransparency = 10;
         }

         instance.styleVersion = 1;
      }

      save();
   }

   public static synchronized void save() {
      try {
         Files.createDirectories(FILE.getParent());

         try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(instance, w);
         }
      } catch (Exception var5) {
         System.err.println("[GlassScout] Konnte Config nicht speichern: " + var5);
      }
   }
}
