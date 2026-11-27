package de.erik.glasshud.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.erik.glasshud.hud.widgets.Widgets;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class HudConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("glasshud.json");
   private static HudConfig instance;
   public static final String[] ACCENT_NAMES = new String[]{"Violett", "Cyan", "Smaragd", "Rosa", "Bernstein", "Weiß"};
   public static final int[] ACCENT_COLORS = new int[]{-7635713, -11546113, -13901702, -37990, -18355, -1447182};
   public int accent = 2;
   public int opacity = 85;
   public boolean hideVanillaEffects = true;
   /** Raster im HUD-Editor anzeigen und daran einrasten */
   public boolean grid = true;
   public Hotkey editorKey = new Hotkey(InputConstants.KEY_F8, false, false, false);
   public int configVersion = 0;
   public Map<String, HudConfig.W> widgets = new LinkedHashMap<>();

   public int accentColor() {
      return ACCENT_COLORS[Math.floorMod(this.accent, ACCENT_COLORS.length)];
   }

   public HudConfig.W w(String id) {
      return this.widgets.computeIfAbsent(id, Widgets::defaultFor);
   }

   public static HudConfig get() {
      if (instance == null) {
         load();
      }

      return instance;
   }

   public static void load() {
      try {
         if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
               instance = (HudConfig)GSON.fromJson(r, HudConfig.class);
            }
         }
      } catch (Exception var5) {
         System.err.println("[GlassHUD] Konnte Config nicht laden: " + var5);
      }

      if (instance == null) {
         instance = new HudConfig();
      }

      if (instance.widgets == null) {
         instance.widgets = new LinkedHashMap<>();
      }

      if (instance.editorKey == null) {
         instance.editorKey = new Hotkey(InputConstants.KEY_F8, false, false, false);
      }

      if (instance.configVersion < 1) {
         if (instance.editorKey.key == InputConstants.KEY_RSHIFT) {
            instance.editorKey = new Hotkey(InputConstants.KEY_F8, false, false, false);
         }

         instance.configVersion = 1;
      }

      if (instance.configVersion < 2) {
         // neuer Standard der Darstellung: 10 % Transparenz (bisher 0)
         for (HudConfig.W w : instance.widgets.values()) {
            if (w.bgTransparency == 0) {
               w.bgTransparency = 10;
            }
         }

         instance.configVersion = 2;
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
         System.err.println("[GlassHUD] Konnte Config nicht speichern: " + var5);
      }
   }

   public static class W {
      public boolean enabled;
      public int ax;
      public int ay;
      public int offX;
      public int offY;
      public float scale = 1.0F;
      /** Hintergrund: 0 = aus, 1 = Kasten mit Rand, 2 = nur schwarz ohne Rand */
      public int bgStyle = 1;
      /** Transparenz des Hintergrunds in Prozent (0 = voll deckend) */
      public int bgTransparency = 10;
      public Map<String, Integer> opts = new HashMap<>();

      public W() {
      }

      public W(boolean enabled, int ax, int ay, int offX, int offY) {
         this.enabled = enabled;
         this.ax = ax;
         this.ay = ay;
         this.offX = offX;
         this.offY = offY;
      }

      public HudConfig.W copy() {
         HudConfig.W w = new HudConfig.W(this.enabled, this.ax, this.ay, this.offX, this.offY);
         w.scale = this.scale;
         w.bgStyle = this.bgStyle;
         w.bgTransparency = this.bgTransparency;
         w.opts = new HashMap<>(this.opts);
         return w;
      }
   }
}
