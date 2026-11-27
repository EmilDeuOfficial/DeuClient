package de.erik.discordglass.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class DiscordState {
   public volatile DiscordState.Status status = DiscordState.Status.DISCONNECTED;
   public volatile String statusDetail = "";
   public volatile String userId = "";
   public volatile String userName = "";
   public volatile String channelId;
   public volatile String channelName;
   public volatile String guildId;
   public volatile String guildName;
   public volatile String voiceConnection = "";
   public volatile float ping = -1.0F;
   public volatile boolean selfMute;
   public volatile boolean selfDeaf;
   private final Map<String, DiscordState.Member> members = new LinkedHashMap<>();
   public final List<DiscordState.Guild> guilds = new CopyOnWriteArrayList<>();
   public final Map<String, List<DiscordState.Channel>> channelsByGuild = Collections.synchronizedMap(new LinkedHashMap<>());
   public final List<DiscordState.Notification> notifications = new CopyOnWriteArrayList<>();
   /** Textkanaele je Server und Adressen der Server-Icons ("" = hat keins) */
   public final Map<String, List<DiscordState.Channel>> textByGuild = new java.util.concurrent.ConcurrentHashMap<>();
   public final Map<String, String> guildIcons = new java.util.concurrent.ConcurrentHashMap<>();
   /** Geoeffnete Unterhaltung im Text-Reiter; Server-Id null = Direktnachricht */
   public volatile String textChannelId;
   public volatile String textChannelName;
   public volatile String textGuildId;
   /** Hinweis statt der Nachrichten (laedt, nicht lesbar), sonst leer */
   public volatile String textStatus = "";
   public final List<DiscordState.Message> messages = new CopyOnWriteArrayList<>();

   /** Art des Sprachkanals: 1 Direktanruf, 3 Gruppe, 2 Server-Kanal; -1 ohne Call */
   public volatile int channelType = -1;

   private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\u0001([^\u0001]*)\u0001");

   /** Platzhalter in einer Nachricht (Anhang, Erwaehnung); uebersetzt wird erst beim Anzeigen */
   public static String placeholder(String key) {
      return "\u0001" + key + "\u0001";
   }

   /** Text einer Nachricht mit Platzhaltern in der aktuellen Spielsprache */
   public static String readableText(String text) {
      return PLACEHOLDER.matcher(text).replaceAll(r -> java.util.regex.Matcher.quoteReplacement(eu.deusmp.deucore.Tr.t(r.group(1))));
   }

   public void closeText() {
      this.textChannelId = null;
      this.textChannelName = null;
      this.textGuildId = null;
      this.textStatus = "";
      this.messages.clear();
   }

   public boolean inCall() {
      return this.channelId != null;
   }

   public synchronized DiscordState.Member member(String id, String name) {
      DiscordState.Member m = this.members.get(id);
      if (m == null) {
         m = new DiscordState.Member(id, name);
         this.members.put(id, m);
      } else if (name != null && !name.isEmpty()) {
         m.name = name;
      }

      return m;
   }

   public synchronized DiscordState.Member findMember(String id) {
      return this.members.get(id);
   }

   public synchronized void removeMember(String id) {
      this.members.remove(id);
   }

   public synchronized void clearMembers() {
      this.members.clear();
   }

   public synchronized List<DiscordState.Member> members() {
      return new ArrayList<>(this.members.values());
   }

   public void clearCall() {
      this.channelId = null;
      this.channelType = -1;
      this.channelName = null;
      this.guildId = null;
      this.guildName = null;
      this.voiceConnection = "";
      this.ping = -1.0F;
      this.clearMembers();
   }

   public void pushNotification(String title, String body) {
      this.notifications.add(new DiscordState.Notification(title, body, System.currentTimeMillis()));

      while (this.notifications.size() > 5) {
         this.notifications.remove(0);
      }
   }

   public record Channel(String id, String name, int type) {
   }

   public record Guild(String id, String name) {
   }

   public record Message(String id, String authorId, String author, String avatar, String text, long time) {
   }

   public static class Member {
      public final String id;
      public volatile String name;
      /** Adresse des Profilbilds, "" = keins */
      public volatile String avatar = "";
      public volatile boolean selfMute;
      public volatile boolean selfDeaf;
      public volatile boolean serverMute;
      public volatile boolean serverDeaf;
      public volatile boolean speaking;
      public volatile long lastSpoke;

      public Member(String id, String name) {
         this.id = id;
         this.name = name;
      }

      public boolean muted() {
         return this.selfMute || this.serverMute;
      }

      public boolean deafened() {
         return this.selfDeaf || this.serverDeaf;
      }
   }

   public record Notification(String title, String body, long time) {
   }

   public static enum Status {
      DISCONNECTED("Getrennt", -7696488),
      WAITING("Warte auf Discord", -7696488),
      CONNECTING("Verbinde…", -1002958),
      AUTHORIZING("Bitte in Discord bestätigen", -1002958),
      CONNECTED("Verbunden", -14441126),
      ERROR("Fehler", -901309);

      public final String label;
      public final int color;

      private Status(String label, int color) {
         this.label = label;
         this.color = color;
      }
   }
}
