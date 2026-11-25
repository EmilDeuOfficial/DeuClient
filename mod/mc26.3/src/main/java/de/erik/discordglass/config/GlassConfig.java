package de.erik.discordglass.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class GlassConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("discordglass.json");
   private static GlassConfig instance;
   public boolean useOwnApp = false;
   public String tokenClientId = "";
   public String clientId = "";
   public String clientSecret = "";
   public String redirectUri = "http://localhost";
   public String accessToken = "";
   public String refreshToken = "";
   public boolean autoConnect = true;
   public boolean richPresence = true;
   public boolean hudEnabled = true;
   public int hudCorner = 0;
   public boolean hudOnlySpeaking = false;
   public boolean notifications = true;
   public int hudOpacity = 70;
   public Hotkey menuKey = new Hotkey(28, true, false, false);
   public Hotkey muteKey = new Hotkey(16, true, false, false);
   public Hotkey deafenKey = new Hotkey(7, true, false, false);
   public Hotkey hudKey = new Hotkey(11, true, false, false);
   public Hotkey leaveKey = new Hotkey();
   public int layoutVersion = 0;

   public void adaptDefaultsToLayout() {
      if (this.layoutVersion < 1) {
         remap(this.menuKey, 28, 'y');
         remap(this.muteKey, 16, 'm');
         remap(this.deafenKey, 7, 'd');
         remap(this.hudKey, 11, 'h');
         this.layoutVersion = 1;
         save();
      }
   }

   private static void remap(Hotkey hk, int usDefault, char letter) {
      if (hk.key == usDefault && hk.ctrl && !hk.shift && !hk.alt) {
         hk.key = Hotkey.layoutKey(letter, usDefault);
      }
   }

   public static GlassConfig get() {
      if (instance == null) {
         load();
      }

      return instance;
   }

   public static void load() {
      try {
         if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
               instance = (GlassConfig)GSON.fromJson(r, GlassConfig.class);
            }
         }
      } catch (Exception var5) {
         System.err.println("[DiscordGlass] Konnte Config nicht laden: " + var5);
      }

      if (instance == null) {
         instance = new GlassConfig();
      }

      if (instance.menuKey == null) {
         instance.menuKey = new Hotkey(28, true, false, false);
      }

      if (instance.muteKey == null) {
         instance.muteKey = new Hotkey();
      }

      if (instance.deafenKey == null) {
         instance.deafenKey = new Hotkey();
      }

      if (instance.hudKey == null) {
         instance.hudKey = new Hotkey();
      }

      if (instance.leaveKey == null) {
         instance.leaveKey = new Hotkey();
      }

      if (instance.tokenClientId == null) {
         instance.tokenClientId = "";
      }

      if (instance.tokenClientId.isEmpty() && !instance.accessToken.isEmpty() && !instance.clientId.isBlank()) {
         instance.useOwnApp = true;
         instance.tokenClientId = instance.clientId.trim();
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
         System.err.println("[DiscordGlass] Konnte Config nicht speichern: " + var5);
      }
   }
}
