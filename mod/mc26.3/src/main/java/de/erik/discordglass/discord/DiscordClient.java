package de.erik.discordglass.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.erik.discordglass.config.GlassConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class DiscordClient {
   private static final String[] OWN_APP_SCOPES = new String[]{"rpc", "rpc.voice.read", "rpc.voice.write", "rpc.notifications.read", "identify", "messages.read"};
   public static final String STREAMKIT_CLIENT_ID = "207646673902501888";
   private static final String[] STREAMKIT_SCOPES = new String[]{"rpc", "messages.read", "rpc.notifications.read"};
   private static final long RETRY_MS = 5000L;
   private static final String[] CHANNEL_EVENTS = new String[]{
      "VOICE_STATE_CREATE", "VOICE_STATE_UPDATE", "VOICE_STATE_DELETE", "SPEAKING_START", "SPEAKING_STOP"
   };
   public static final DiscordClient INSTANCE = new DiscordClient();
   public final DiscordState state = new DiscordState();
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private final Map<String, BiConsumer<JsonObject, Boolean>> pending = new ConcurrentHashMap<>();
   private volatile IpcPipe pipe;
   private volatile Thread worker;
   private volatile boolean wantConnected;
   private volatile boolean userDisconnected;
   private volatile long nextRetry;
   private volatile String subscribedChannel;
   private final long sessionStart = System.currentTimeMillis() / 1000L;
   private volatile String lastActivity = "";
   /** Stummschaltung ohne Taubschalten: gilt wieder, sobald der Ton angeht */
   private volatile boolean muteWithoutDeaf;

   private DiscordClient() {
   }

   public boolean isConnected() {
      return this.state.status == DiscordState.Status.CONNECTED;
   }

   public boolean isRunning() {
      return this.worker != null && this.worker.isAlive() || this.state.status == DiscordState.Status.CONNECTED;
   }

   public synchronized void connect() {
      this.wantConnected = true;
      this.userDisconnected = false;
      if (!this.isRunning()) {
         if (GlassConfig.get().useOwnApp && GlassConfig.get().clientId.isBlank()) {
            this.state.status = DiscordState.Status.ERROR;
            this.state.statusDetail = eu.deusmp.deucore.Tr.t("Eigene App aktiv, aber keine App-ID eingetragen");
            this.wantConnected = false;
         } else {
            Thread t = new Thread(this::run, "DiscordGlass-IPC");
            t.setDaemon(true);
            this.worker = t;
            t.start();
         }
      }
   }

   public synchronized void disconnect() {
      this.wantConnected = false;
      this.userDisconnected = true;
      this.closePipe();
      this.state.status = DiscordState.Status.DISCONNECTED;
      this.state.statusDetail = "";
      this.state.clearCall();
   }

   public void tick() {
      GlassConfig cfg = GlassConfig.get();
      if (!this.isRunning() && (this.wantConnected || cfg.autoConnect && !this.userDisconnected) && System.currentTimeMillis() > this.nextRetry) {
         this.nextRetry = System.currentTimeMillis() + 5000L;
         this.connect();
      }
   }

   public static boolean ownApp() {
      GlassConfig cfg = GlassConfig.get();
      return cfg.useOwnApp && !cfg.clientId.isBlank();
   }

   public static String clientId() {
      return ownApp() ? GlassConfig.get().clientId.trim() : "207646673902501888";
   }

   public void forgetLogin() {
      GlassConfig cfg = GlassConfig.get();
      cfg.accessToken = "";
      cfg.refreshToken = "";
      GlassConfig.save();
      this.disconnect();
   }

   public void toggleMute() {
      if (this.isConnected()) {
         JsonObject a = new JsonObject();
         boolean mute = !this.state.selfMute;
         a.addProperty("mute", mute);
         if (!mute && this.state.selfDeaf) {
            a.addProperty("deaf", false); // wie in Discord: Mikrofon an hebt auch Taub auf
         }
         this.command("SET_VOICE_SETTINGS", a, (d, err) -> {
            if (!err) {
               this.applyVoiceSettings(d);
            }
         });
      }
   }

   public void toggleDeafen() {
      if (this.isConnected()) {
         JsonObject a = new JsonObject();
         boolean deaf = !this.state.selfDeaf;
         a.addProperty("deaf", deaf);
         a.addProperty("mute", deaf || this.muteWithoutDeaf);
         this.command("SET_VOICE_SETTINGS", a, (d, err) -> {
            if (!err) {
               this.applyVoiceSettings(d);
            }
         });
      }
   }

   public void joinVoice(String channelId) {
      if (this.isConnected()) {
         JsonObject a = new JsonObject();
         a.addProperty("channel_id", channelId);
         a.addProperty("force", true);
         this.command("SELECT_VOICE_CHANNEL", a, (d, err) -> {
            if (err) {
               this.state.pushNotification(eu.deusmp.deucore.Tr.t("Beitreten fehlgeschlagen"), str(d, "message"));
            }
         });
      }
   }

   public void leaveVoice() {
      if (this.isConnected()) {
         JsonObject a = new JsonObject();
         a.add("channel_id", null);
         this.command("SELECT_VOICE_CHANNEL", a, null);
      }
   }

   public void refreshGuilds() {
      if (this.isConnected()) {
         this.command("GET_GUILDS", new JsonObject(), (d, err) -> {
            if (!err) {
               List<DiscordState.Guild> list = new ArrayList<>();

               for (JsonElement e : arr(d, "guilds")) {
                  JsonObject g = e.getAsJsonObject();
                  list.add(new DiscordState.Guild(str(g, "id"), str(g, "name")));
               }

               this.state.guilds.clear();
               this.state.guilds.addAll(list);
            }
         });
      }
   }

   public void loadChannels(String guildId) {
      if (this.isConnected()) {
         JsonObject a = new JsonObject();
         a.addProperty("guild_id", guildId);
         this.command("GET_CHANNELS", a, (d, err) -> {
            if (!err) {
               List<DiscordState.Channel> list = new ArrayList<>();
               List<DiscordState.Channel> text = new ArrayList<>();

               for (JsonElement e : arr(d, "channels")) {
                  JsonObject c = e.getAsJsonObject();
                  int type = c.has("type") ? c.get("type").getAsInt() : 0;
                  if (type == 2 || type == 13) {
                     list.add(new DiscordState.Channel(str(c, "id"), str(c, "name"), type));
                  } else if (type == 0 || type == 5) {
                     text.add(new DiscordState.Channel(str(c, "id"), str(c, "name"), type));
                  }
               }

               this.state.channelsByGuild.put(guildId, list);
               this.state.textByGuild.put(guildId, text);
            }
         });
      }
   }

   /** Icon eines Servers erfragen (je Server einmal) */
   public void loadGuildIcon(String guildId) {
      if (this.isConnected() && this.state.guildIcons.putIfAbsent(guildId, "") == null) {
         JsonObject a = new JsonObject();
         a.addProperty("guild_id", guildId);
         this.command("GET_GUILD", a, (d, err) -> {
            if (!err) {
               this.state.guildIcons.put(guildId, str(d, "icon_url"));
            }
         });
      }
   }

   /* ---------------- Text: lesen (Senden erlaubt Discord anderen Programmen nicht) ---------------- */

   private static final String[] MESSAGE_EVENTS = new String[]{"MESSAGE_CREATE", "MESSAGE_UPDATE", "MESSAGE_DELETE"};

   public void openText(String channelId, String name, String guildId) {
      if (!this.isConnected()) {
         return;
      }

      this.unsubscribeText();
      this.state.closeText();
      this.state.textChannelId = channelId;
      this.state.textChannelName = name;
      this.state.textGuildId = guildId;
      this.state.textStatus = eu.deusmp.deucore.Tr.t("Nachrichten werden geladen…");
      JsonObject a = new JsonObject();
      a.addProperty("channel_id", channelId);
      this.command("GET_CHANNEL", a, (d, err) -> {
         if (!channelId.equals(this.state.textChannelId)) {
            return;
         }

         if (err) {
            this.state.textStatus = eu.deusmp.deucore.Tr.t("Nachrichten nicht lesbar");
            return;
         }

         List<DiscordState.Message> list = new ArrayList<>();

         for (JsonElement e : arr(d, "messages")) {
            if (e.isJsonObject()) {
               DiscordState.Message m = message(e.getAsJsonObject());
               if (m != null) {
                  list.add(m);
               }
            }
         }

         list.sort(java.util.Comparator.comparingLong(DiscordState.Message::time));
         this.state.messages.clear();
         this.state.messages.addAll(list);
         this.state.textStatus = "";

         for (String evt : MESSAGE_EVENTS) {
            this.subscribe(evt, a, true);
         }
      });
   }

   public void closeText() {
      this.unsubscribeText();
      this.state.closeText();
   }

   private void unsubscribeText() {
      String open = this.state.textChannelId;
      if (open != null) {
         JsonObject a = new JsonObject();
         a.addProperty("channel_id", open);

         for (String evt : MESSAGE_EVENTS) {
            this.subscribe(evt, a, false);
         }
      }
   }

   /** Zeigt die Unterhaltung in der Discord-App und holt diese nach vorn (dort kann man schreiben) */
   public void openInDiscord(String channelId, String guildId) {
      if (this.isConnected()) {
         JsonObject a = new JsonObject();
         a.addProperty("channel_id", channelId);
         this.command("SELECT_TEXT_CHANNEL", a, null);
      }

      String guild = guildId == null ? "@me" : guildId;
      if (channelId.matches("\\d+") && guild.matches("\\d+|@me")) {
         this.openLink("discord://-/channels/" + guild + "/" + channelId);
      }
   }

   /** Zeigt das Profil in der Discord-App; dort kann man anschreiben und anrufen */
   public void openProfile(String userId) {
      if (userId.matches("\\d+")) {
         this.openLink("discord://-/users/" + userId);
      }
   }

   /** Nur mit geprueften Kennungen aufrufen: Windows oeffnet die Adresse mit der Discord-App */
   private void openLink(String link) {
      try {
         new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", link).start();
      } catch (IOException e) {
         this.state.pushNotification(eu.deusmp.deucore.Tr.t("Discord ließ sich nicht öffnen"), "");
      }
   }

   private static String avatarUrl(JsonObject user) {
      String id = str(user, "id");
      String hash = str(user, "avatar");
      return id.matches("\\d+") && hash.matches("\\w+") ? "https://cdn.discordapp.com/avatars/" + id + "/" + hash + ".png" : "";
   }

   /** Eine Nachricht aus Discord; null, wenn sie nichts Anzeigbares enthaelt (z. B. Systemmeldungen) */
   private static DiscordState.Message message(JsonObject m) {
      JsonObject author = obj(m, "author");
      String text = readable(str(m, "content").replace("\u0001", ""), arr(m, "mentions"));
      if (arr(m, "attachments").size() > 0 || arr(m, "embeds").size() > 0 && text.isEmpty()) {
         text = (text.isEmpty() ? "" : text + " ") + "[" + DiscordState.placeholder("Anhang") + "]";
      }

      if (arr(m, "sticker_items").size() > 0) {
         text = (text.isEmpty() ? "" : text + " ") + "[Sticker]";
      }

      if (text.isEmpty()) {
         return null;
      }

      long time;
      try {
         time = java.time.OffsetDateTime.parse(str(m, "timestamp")).toInstant().toEpochMilli();
      } catch (Exception e) {
         time = System.currentTimeMillis();
      }

      String nick = str(m, "nick");
      return new DiscordState.Message(str(m, "id"), str(author, "id"), nick.isEmpty() ? displayName(author, "?") : nick, avatarUrl(author), text, time);
   }

   private static final java.util.regex.Pattern MENTION = java.util.regex.Pattern.compile("<(@!?|@&|#|a?:\\w+:)(\\d+)>");

   /** Discords Platzhalter fuer Erwaehnungen und eigene Emojis in lesbaren Text */
   private static String readable(String content, JsonArray mentions) {
      java.util.regex.Matcher m = MENTION.matcher(content);
      StringBuilder out = new StringBuilder();

      while (m.find()) {
         String kind = m.group(1);
         String shown;
         if (kind.equals("@&")) {
            shown = "@" + DiscordState.placeholder("Rolle");
         } else if (kind.equals("#")) {
            shown = "#" + DiscordState.placeholder("Kanal");
         } else if (kind.startsWith("@")) {
            shown = "@" + DiscordState.placeholder("jemand");
            for (JsonElement e : mentions) {
               if (e.isJsonObject() && str(e.getAsJsonObject(), "id").equals(m.group(2))) {
                  shown = "@" + displayName(e.getAsJsonObject(), "?").replace("\u0001", "");
               }
            }
         } else {
            shown = kind.substring(kind.indexOf(':')); // :name:
         }

         m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(shown));
      }

      m.appendTail(out);
      return out.toString().strip();
   }

   private void onMessage(String evt, JsonObject data) {
      if (!str(data, "channel_id").equals(this.state.textChannelId)) {
         return;
      }

      JsonObject raw = obj(data, "message");
      String id = str(raw, "id");
      if (evt.equals("MESSAGE_DELETE")) {
         this.state.messages.removeIf(x -> x.id().equals(id));
         return;
      }

      DiscordState.Message m = message(raw);
      if (m == null) {
         return;
      }

      for (int i = 0; i < this.state.messages.size(); i++) {
         if (this.state.messages.get(i).id().equals(id)) {
            this.state.messages.set(i, m);
            return;
         }
      }

      if (evt.equals("MESSAGE_CREATE")) {
         this.state.messages.add(m);

         while (this.state.messages.size() > 100) {
            this.state.messages.remove(0);
         }
      }
   }

   /** Direktnachrichten aus Benachrichtigungen kommen in die Freundesliste, damit man sie oeffnen und anrufen kann */
   private void rememberDirectChat(JsonObject data) {
      String channel = str(data, "channel_id");
      if (channel.isEmpty()) {
         return;
      }

      String title = str(data, "title");
      String icon = str(data, "icon_url");
      JsonObject author = obj(obj(data, "message"), "author");
      JsonObject a = new JsonObject();
      a.addProperty("channel_id", channel);
      this.command("GET_CHANNEL", a, (d, err) -> {
         int type = !err && d.has("type") ? d.get("type").getAsInt() : -1;
         if (type == 1 || type == 3) { // Direktnachricht oder Gruppe
            String user = type == 1 && !str(author, "id").equals(this.state.userId) ? str(author, "id") : "";
            String name = str(d, "name");
            if (name.isEmpty()) {
               name = user.isEmpty() ? title : displayName(author, title);
            }

            String picture = user.isEmpty() || avatarUrl(author).isEmpty() ? icon : avatarUrl(author);
            GlassConfig.get().rememberFriend(channel, user, name, picture, true);
         }
      });
   }

   public void updateActivity(String details, String stateText) {
      if (this.pipe != null && this.state.status == DiscordState.Status.CONNECTED) {
         if (ownApp()) {
            boolean enabled = GlassConfig.get().richPresence;
            String key = enabled + "|" + details + "|" + stateText;
            if (!key.equals(this.lastActivity)) {
               this.lastActivity = key;
               JsonObject a = new JsonObject();
               a.addProperty("pid", ProcessHandle.current().pid());
               if (enabled) {
                  JsonObject act = new JsonObject();
                  act.addProperty("details", details);
                  act.addProperty("state", stateText);
                  JsonObject ts = new JsonObject();
                  ts.addProperty("start", this.sessionStart);
                  act.add("timestamps", ts);
                  a.add("activity", act);
               } else {
                  a.add("activity", null);
               }

               this.command("SET_ACTIVITY", a, null);
            }
         }
      }
   }

   private void run() {
      GlassConfig cfg = GlassConfig.get();
      this.state.status = DiscordState.Status.CONNECTING;
      this.state.statusDetail = "";

      try {
         try {
            try {
               this.pipe = IpcPipe.open();
            } catch (IOException var10) {
               this.state.status = DiscordState.Status.WAITING;
               this.state.statusDetail = eu.deusmp.deucore.Tr.t("Öffne Discord, die Mod verbindet sich automatisch.");
               return;
            }

            JsonObject hs = new JsonObject();
            hs.addProperty("v", 1);
            hs.addProperty("client_id", clientId());
            this.pipe.write(0, hs.toString());

            while (this.wantConnected && this.pipe != null) {
               IpcPipe.Frame f = this.pipe.poll();
               if (f == null) {
                  Thread.sleep(15L);
               } else {
                  switch (f.op()) {
                     case 1:
                        this.handleFrame(JsonParser.parseString(f.json()).getAsJsonObject());
                        break;
                     case 2:
                        JsonObject o = JsonParser.parseString(f.json()).getAsJsonObject();
                        throw new IOException(eu.deusmp.deucore.Tr.t("Discord hat die Verbindung geschlossen") + ": " + str(o, "message"));
                     case 3:
                        this.pipe.write(4, f.json());
                  }
               }
            }

            return;
         } catch (InterruptedException var11) {
         } catch (Exception var12) {
            if (this.wantConnected) {
               this.state.status = DiscordState.Status.ERROR;
               this.state.statusDetail = var12.getMessage() == null ? var12.toString() : eu.deusmp.deucore.Tr.t(var12.getMessage());
               return;
            }
         }
      } finally {
         this.closePipe();
         this.state.clearCall();
         if (this.state.status != DiscordState.Status.ERROR && this.state.status != DiscordState.Status.WAITING) {
            this.state.status = DiscordState.Status.DISCONNECTED;
         }

         this.nextRetry = System.currentTimeMillis() + 5000L;
      }
   }

   private void closePipe() {
      IpcPipe p = this.pipe;
      this.pipe = null;
      if (p != null) {
         p.close();
      }

      this.pending.clear();
      this.subscribedChannel = null;
      this.lastActivity = "";
      this.state.closeText();
      this.state.guildIcons.values().removeIf(String::isEmpty); // noch einmal fragen
   }

   private void send(JsonObject payload) {
      IpcPipe p = this.pipe;
      if (p != null) {
         try {
            p.write(1, payload.toString());
         } catch (IOException var4) {
            this.state.status = DiscordState.Status.ERROR;
            this.state.statusDetail = eu.deusmp.deucore.Tr.t("Senden fehlgeschlagen") + ": " + var4.getMessage();
            this.closePipe();
         }
      }
   }

   private void command(String cmd, JsonObject args, BiConsumer<JsonObject, Boolean> cb) {
      JsonObject o = new JsonObject();
      String nonce = UUID.randomUUID().toString();
      o.addProperty("cmd", cmd);
      o.add("args", args);
      o.addProperty("nonce", nonce);
      if (cb != null) {
         this.pending.put(nonce, cb);
      }

      this.send(o);
   }

   private void subscribe(String evt, JsonObject args, boolean sub) {
      JsonObject o = new JsonObject();
      o.addProperty("cmd", sub ? "SUBSCRIBE" : "UNSUBSCRIBE");
      o.addProperty("evt", evt);
      o.add("args", args == null ? new JsonObject() : args);
      o.addProperty("nonce", UUID.randomUUID().toString());
      this.send(o);
   }

   private void handleFrame(JsonObject msg) {
      String cmd = str(msg, "cmd");
      String evt = str(msg, "evt");
      JsonObject data = msg.has("data") && msg.get("data").isJsonObject() ? msg.getAsJsonObject("data") : new JsonObject();
      String nonce = str(msg, "nonce");
      if (!nonce.isEmpty() && this.pending.containsKey(nonce)) {
         BiConsumer<JsonObject, Boolean> cb = this.pending.remove(nonce);
         cb.accept(data, "ERROR".equals(evt));
      } else if ("DISPATCH".equals(cmd)) {
         switch (evt) {
            case "READY":
               this.onReady(data);
               break;
            case "VOICE_SETTINGS_UPDATE":
               this.applyVoiceSettings(data);
               break;
            case "VOICE_CHANNEL_SELECT":
               this.onChannelSelect(str(data, "channel_id"));
               break;
            case "VOICE_CONNECTION_STATUS":
               this.state.voiceConnection = str(data, "state");
               if (data.has("last_ping") && !data.get("last_ping").isJsonNull()) {
                  this.state.ping = data.get("last_ping").getAsFloat();
               }
               break;
            case "VOICE_STATE_CREATE":
            case "VOICE_STATE_UPDATE":
               this.applyVoiceState(data);
               break;
            case "VOICE_STATE_DELETE":
               JsonObject u = obj(data, "user");
               this.state.removeMember(str(u, "id"));
               break;
            case "SPEAKING_START":
            case "SPEAKING_STOP":
               DiscordState.Member m = this.state.findMember(str(data, "user_id"));
               if (m != null) {
                  m.speaking = evt.equals("SPEAKING_START");
                  m.lastSpoke = System.currentTimeMillis();
               }
               break;
            case "MESSAGE_CREATE":
            case "MESSAGE_UPDATE":
            case "MESSAGE_DELETE":
               this.onMessage(evt, data);
               break;
            case "NOTIFICATION_CREATE":
               this.rememberDirectChat(data);
               if (GlassConfig.get().notifications) {
                  this.state.pushNotification(str(data, "title"), str(data, "body"));
               }
         }
      }
   }

   private void onReady(JsonObject data) {
      JsonObject user = obj(data, "user");
      this.state.userId = str(user, "id");
      this.state.userName = displayName(user, "");
      GlassConfig cfg = GlassConfig.get();
      if (!clientId().equals(cfg.tokenClientId)) {
         cfg.accessToken = "";
         cfg.refreshToken = "";
      }

      if (!cfg.accessToken.isEmpty()) {
         this.authenticate(cfg.accessToken, true);
      } else {
         this.authorize();
      }
   }

   private void authorize() {
      GlassConfig cfg = GlassConfig.get();
      boolean own = ownApp();
      if (own && cfg.clientSecret.isBlank()) {
         this.state.status = DiscordState.Status.ERROR;
         this.state.statusDetail = eu.deusmp.deucore.Tr.t("Client-Secret fehlt (Einstellungen, Eigene Discord-App)");
      } else {
         this.state.status = DiscordState.Status.AUTHORIZING;
         this.state.statusDetail = eu.deusmp.deucore.Tr.t("Ein Fenster in Discord wartet auf deine Bestätigung");
         JsonObject a = new JsonObject();
         a.addProperty("client_id", clientId());
         JsonArray scopes = new JsonArray();

         for (String s : own ? OWN_APP_SCOPES : STREAMKIT_SCOPES) {
            scopes.add(s);
         }

         a.add("scopes", scopes);
         this.command("AUTHORIZE", a, (d, err) -> {
            if (err) {
               this.state.status = DiscordState.Status.ERROR;
               this.state.statusDetail = eu.deusmp.deucore.Tr.t("Autorisierung abgelehnt") + ": " + str(d, "message");
            } else {
               String code = str(d, "code");
               if (own) {
                  Thread.ofVirtual().start(() -> this.exchangeToken("authorization_code", code));
               } else {
                  Thread.ofVirtual().start(() -> this.exchangeStreamKit(code));
               }
            }
         });
      }
   }

   private void exchangeStreamKit(String code) {
      GlassConfig cfg = GlassConfig.get();

      try {
         JsonObject body = new JsonObject();
         body.addProperty("code", code);
         HttpRequest req = HttpRequest.newBuilder(URI.create("https://streamkit.discord.com/overlay/token"))
            .timeout(Duration.ofSeconds(15L))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body.toString()))
            .build();
         HttpResponse<String> res = this.http.send(req, BodyHandlers.ofString());
         JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
         if (!json.has("access_token")) {
            throw new IOException("HTTP " + res.statusCode());
         }

         cfg.accessToken = str(json, "access_token");
         cfg.refreshToken = "";
         cfg.tokenClientId = "207646673902501888";
         GlassConfig.save();
         this.authenticate(cfg.accessToken, false);
      } catch (Exception var7) {
         this.state.status = DiscordState.Status.ERROR;
         this.state.statusDetail = eu.deusmp.deucore.Tr.t("Anmeldung fehlgeschlagen") + " (" + var7.getMessage() + "). " + eu.deusmp.deucore.Tr.t("Versuche es erneut oder nutze eine eigene App.");
      }
   }

   private void exchangeToken(String grant, String value) {
      GlassConfig cfg = GlassConfig.get();

      try {
         StringBuilder body = new StringBuilder();
         body.append("client_id=").append(enc(cfg.clientId.trim()));
         body.append("&client_secret=").append(enc(cfg.clientSecret.trim()));
         body.append("&grant_type=").append(enc(grant));
         if (grant.equals("authorization_code")) {
            body.append("&code=").append(enc(value));
            body.append("&redirect_uri=").append(enc(cfg.redirectUri.trim()));
         } else {
            body.append("&refresh_token=").append(enc(value));
         }

         HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/oauth2/token"))
            .timeout(Duration.ofSeconds(15L))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(body.toString()))
            .build();
         HttpResponse<String> res = this.http.send(req, BodyHandlers.ofString());
         JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
         if (res.statusCode() != 200 || !json.has("access_token")) {
            throw new IOException(str(json, "error_description").isEmpty() ? res.body() : str(json, "error_description"));
         }

         cfg.accessToken = str(json, "access_token");
         cfg.refreshToken = str(json, "refresh_token");
         cfg.tokenClientId = cfg.clientId.trim();
         GlassConfig.save();
         this.authenticate(cfg.accessToken, false);
      } catch (Exception var8) {
         if (grant.equals("refresh_token")) {
            cfg.accessToken = "";
            cfg.refreshToken = "";
            GlassConfig.save();
            this.authorize();
         } else {
            this.state.status = DiscordState.Status.ERROR;
            this.state.statusDetail = eu.deusmp.deucore.Tr.t("Token-Tausch fehlgeschlagen") + ": " + var8.getMessage();
         }
      }
   }

   private void authenticate(String token, boolean allowRefresh) {
      JsonObject a = new JsonObject();
      a.addProperty("access_token", token);
      this.command("AUTHENTICATE", a, (d, err) -> {
         if (!err) {
            JsonObject user = obj(d, "user");
            if (!str(user, "id").isEmpty()) {
               this.state.userId = str(user, "id");
               this.state.userName = displayName(user, this.state.userName);
            }

            this.state.status = DiscordState.Status.CONNECTED;
            this.state.statusDetail = "";
            this.onAuthenticated();
         } else {
            GlassConfig cfg = GlassConfig.get();
            if (allowRefresh && !cfg.refreshToken.isEmpty()) {
               String rt = cfg.refreshToken;
               Thread.ofVirtual().start(() -> this.exchangeToken("refresh_token", rt));
            } else {
               cfg.accessToken = "";
               GlassConfig.save();
               this.authorize();
            }
         }
      });
   }

   private void onAuthenticated() {
      this.subscribe("VOICE_SETTINGS_UPDATE", null, true);
      this.subscribe("VOICE_CHANNEL_SELECT", null, true);
      this.subscribe("VOICE_CONNECTION_STATUS", null, true);
      this.subscribe("NOTIFICATION_CREATE", null, true);
      this.command("GET_VOICE_SETTINGS", new JsonObject(), (d, err) -> {
         if (!err) {
            this.applyVoiceSettings(d);
         }
      });
      this.command("GET_SELECTED_VOICE_CHANNEL", new JsonObject(), (d, err) -> {
         if (!err && d.has("id")) {
            this.applyChannel(d);
         }
      });
      this.refreshGuilds();
   }

   private void applyVoiceSettings(JsonObject d) {
      if (d.has("deaf") && !d.get("deaf").isJsonNull()) {
         this.state.selfDeaf = d.get("deaf").getAsBoolean();
      }

      if (d.has("mute") && !d.get("mute").isJsonNull()) {
         this.state.selfMute = d.get("mute").getAsBoolean();
         if (!this.state.selfDeaf) {
            this.muteWithoutDeaf = this.state.selfMute;
         }
      }

      if (this.state.selfDeaf) {
         this.state.selfMute = true;
      }
   }

   private void onChannelSelect(String channelId) {
      if (channelId.isEmpty()) {
         this.setChannelSubscriptions(null);
         this.state.clearCall();
      } else {
         JsonObject a = new JsonObject();
         a.addProperty("channel_id", channelId);
         this.command("GET_CHANNEL", a, (d, err) -> {
            if (!err) {
               this.applyChannel(d);
            }
         });
      }
   }

   private void applyChannel(JsonObject ch) {
      String id = str(ch, "id");
      this.state.clearMembers();
      this.state.channelId = id;
      this.state.channelType = ch.has("type") && !ch.get("type").isJsonNull() ? ch.get("type").getAsInt() : -1;
      this.state.channelName = str(ch, "name");
      String guildId = str(ch, "guild_id");
      this.state.guildId = guildId.isEmpty() ? null : guildId;
      this.state.guildName = null;

      for (JsonElement e : arr(ch, "voice_states")) {
         this.applyVoiceState(e.getAsJsonObject());
      }

      this.setChannelSubscriptions(id);
      if (this.state.channelType == 3 && !str(ch, "name").isEmpty()) {
         GlassConfig.get().rememberFriend(id, "", str(ch, "name"), "", false);
      }

      if (!guildId.isEmpty()) {
         JsonObject a = new JsonObject();
         a.addProperty("guild_id", guildId);
         this.command("GET_GUILD", a, (d, err) -> {
            if (!err && guildId.equals(this.state.guildId)) {
               this.state.guildName = str(d, "name");
            }
         });
      } else {
         this.state.guildName = eu.deusmp.deucore.Tr.t("Direktanruf");
      }
   }

   private void setChannelSubscriptions(String channelId) {
      String old = this.subscribedChannel;
      if (old != null && !old.equals(channelId)) {
         JsonObject a = new JsonObject();
         a.addProperty("channel_id", old);

         for (String evt : CHANNEL_EVENTS) {
            this.subscribe(evt, a, false);
         }
      }

      if (channelId != null && !channelId.equals(old)) {
         JsonObject a = new JsonObject();
         a.addProperty("channel_id", channelId);

         for (String evt : CHANNEL_EVENTS) {
            this.subscribe(evt, a, true);
         }
      }

      this.subscribedChannel = channelId;
   }

   private void applyVoiceState(JsonObject vs) {
      JsonObject user = obj(vs, "user");
      String id = str(user, "id");
      if (!id.isEmpty()) {
         String nick = str(vs, "nick");
         DiscordState.Member m = this.state.member(id, nick.isEmpty() ? displayName(user, id) : nick);
         m.avatar = avatarUrl(user);
         JsonObject v = obj(vs, "voice_state");
         m.selfMute = bool(v, "self_mute");
         m.selfDeaf = bool(v, "self_deaf");
         m.serverMute = bool(v, "mute") || bool(v, "suppress");
         m.serverDeaf = bool(v, "deaf");
         String channel = this.state.channelId;
         if (this.state.channelType == 1 && channel != null && !id.equals(this.state.userId)) { // Direktanruf
            GlassConfig.get().rememberFriend(channel, id, displayName(user, m.name), m.avatar, false);
         }

         if (id.equals(this.state.userId)) {
            this.state.selfMute = m.selfMute || m.selfDeaf;
            this.state.selfDeaf = m.selfDeaf;
         }
      }
   }

   private static String displayName(JsonObject user, String fallback) {
      String g = str(user, "global_name");
      if (!g.isEmpty()) {
         return g;
      } else {
         String u = str(user, "username");
         return u.isEmpty() ? fallback : u;
      }
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

   static String str(JsonObject o, String k) {
      if (o != null && o.has(k) && !o.get(k).isJsonNull()) {
         JsonElement e = o.get(k);
         return e.isJsonPrimitive() ? e.getAsString() : e.toString();
      } else {
         return "";
      }
   }

   static boolean bool(JsonObject o, String k) {
      return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
   }

   static JsonObject obj(JsonObject o, String k) {
      return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
   }

   static JsonArray arr(JsonObject o, String k) {
      return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
   }
}
