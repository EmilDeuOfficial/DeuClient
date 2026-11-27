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
   /** 0 oben links, 1 oben rechts, 2 unten links, 3 unten rechts */
   public int hudCorner = 1;
   public int cornerVersion = 0;
   public boolean hudOnlySpeaking = false;
   public boolean notifications = true;
   /** Darstellung wie bei den HUD-Elementen */
   public float scale = 1.0F;
   public int bgStyle = 1;
   public int bgTransparency = 10;
   public Hotkey menuKey = new Hotkey(28, true, false, false);
   public Hotkey muteKey = new Hotkey(16, true, false, false);
   public Hotkey deafenKey = new Hotkey(7, true, false, false);
   public Hotkey hudKey = new Hotkey(11, true, false, false);
   public Hotkey leaveKey = new Hotkey();
   public int layoutVersion = 0;
   /**
    * Eigene Freundesliste. Discord gibt seine nicht heraus: Eintraege entstehen aus Direktnachrichten, Direktanrufen
    * und ueber den Stern bei Personen im Call oder Chat.
    */
   @com.google.gson.annotations.SerializedName(value = "friends", alternate = "recentChats")
   public java.util.List<GlassConfig.Friend> friends = new java.util.concurrent.CopyOnWriteArrayList<>();

   public static class Friend {
      /** Kanal der Direktnachricht bzw. Gruppe, "" = noch unbekannt */
      public String id = "";
      /** Discord-Nutzer, "" = unbekannt oder Gruppe */
      public String userId = "";
      public String name = "";
      public String icon = "";

      public Friend() {
      }

      public Friend(String id, String userId, String name, String icon) {
         this.id = id;
         this.userId = userId;
         this.name = name;
         this.icon = icon;
      }

      public boolean hasChat() {
         return !this.id.isEmpty();
      }

      private boolean matches(String channel, String user) {
         return !channel.isEmpty() && channel.equals(this.id) || !user.isEmpty() && user.equals(this.userId);
      }
   }

   public GlassConfig.Friend friendByUser(String userId) {
      return userId.isEmpty() ? null : this.friends.stream().filter(f -> userId.equals(f.userId)).findFirst().orElse(null);
   }

   /**
    * Legt einen Eintrag an oder ergaenzt ihn; leere Angaben ueberschreiben nichts. Zusammengefuehrt wird nur ueber
    * Kanal oder Nutzer, nie ueber den Namen (sonst riefe man die falsche Person an).
    */
   public synchronized void rememberFriend(String channel, String userId, String name, String icon, boolean toFront) {
      GlassConfig.Friend next = new GlassConfig.Friend(channel, userId, name, icon);
      int at = -1;

      for (int i = this.friends.size() - 1; i >= 0; i--) {
         GlassConfig.Friend old = this.friends.get(i);
         if (old.matches(channel, userId)) {
            if (next.id.isEmpty()) next.id = old.id;
            if (next.userId.isEmpty()) next.userId = old.userId;
            if (next.name.isEmpty()) next.name = old.name;
            if (next.icon.isEmpty()) next.icon = old.icon;
            if (at >= 0) {
               this.friends.remove(at); // zwei Eintraege derselben Person werden einer
            } else if (!toFront && next.id.equals(old.id) && next.userId.equals(old.userId) && next.name.equals(old.name) && next.icon.equals(old.icon)) {
               return;
            }

            at = i;
         }
      }

      if (next.name.isEmpty()) {
         return;
      }

      if (at >= 0 && !toFront) {
         this.friends.set(at, next);
      } else {
         if (at >= 0) this.friends.remove(at);
         this.friends.add(0, next);
      }

      while (this.friends.size() > 100) {
         this.friends.remove(this.friends.size() - 1);
      }

      save();
   }

   public synchronized void removeFriend(GlassConfig.Friend friend) {
      this.friends.remove(friend);
      save();
   }

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

      instance.friends = instance.friends == null ? new java.util.concurrent.CopyOnWriteArrayList<>()
            : new java.util.concurrent.CopyOnWriteArrayList<>(instance.friends);
      instance.friends.removeIf(f -> f == null || f.name == null);
      instance.friends.forEach(f -> {
         if (f.id == null) f.id = "";
         if (f.userId == null) f.userId = "";
         if (f.icon == null) f.icon = "";
      });

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

      if (instance.cornerVersion < 1) {
         // neuer Standard: oben rechts statt oben links (dort liegen die Koordinaten)
         if (instance.hudCorner == 0) {
            instance.hudCorner = 1;
         }

         instance.cornerVersion = 1;
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
