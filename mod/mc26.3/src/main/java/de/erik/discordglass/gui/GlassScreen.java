package de.erik.discordglass.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.erik.discordglass.config.GlassConfig;
import de.erik.discordglass.config.Hotkey;
import de.erik.discordglass.discord.DiscordClient;
import de.erik.discordglass.discord.DiscordState;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Features;
import eu.deusmp.deucore.screen.FeatureSettingsScreen;
import eu.deusmp.deucore.screen.Ui;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Discord-Fenster im Stil des Mod-Menues: eine Kopfzeile, zwei Reiter.
 * "Call": links Freunde und Server bzw. Sprachkanaele, rechts der laufende Anruf. "Text": links Freunde und Textkanaele,
 * rechts die Nachrichten (lesen und in Discord antworten). "Einstellungen": Overlay, Verbindung, Tasten.
 */
public class GlassScreen extends Screen {
   private static final int ROW = 20;
   private static final int RED = 0xFFFF6B7D;
   private enum Page { CALL, TEXT, SETTINGS }
   private static GlassScreen.Page lastPage = GlassScreen.Page.CALL;

   private final Screen parent;
   private GlassScreen.Page page = lastPage;
   private final List<Hit> hits = new ArrayList<>();
   private final List<Scroll> scrolls = new ArrayList<>();
   private Hotkey capturing;
   private String selectedGuild;
   private String filter = "";
   private int listScroll;
   private int memberScroll;
   private int leftScroll;
   private int rightScroll;
   private String textGuild;
   private int textListScroll;
   /** Abstand zur neuesten Nachricht in Bildpunkten */
   private int messageScroll;
   /** Im Text-Reiter angewaehlte Person, zu der noch kein Chat bekannt ist */
   private String friendWithoutChat;
   /** Hilfetext unter der Maus, in diesem Bild gesetzt */
   private String tip;
   private int[] demoMouse;
   private EditBox searchBox;
   private EditBox idBox;
   private EditBox secretBox;

   public GlassScreen(Screen parent) {
      super(Component.literal("Discord"));
      this.parent = parent;
   }

   /** Nur fuer Testbilder: Reiter, geoeffneter Server und Tastenbelegung im Gange */
   public static GlassScreen demo(Screen parent, int tab, String guild, boolean captureMute) {
      lastPage = GlassScreen.Page.values()[tab];
      GlassScreen screen = new GlassScreen(parent);
      screen.selectedGuild = guild;
      if (captureMute) {
         screen.capturing = GlassConfig.get().muteKey;
      }

      return screen;
   }

   /** Nur fuer Testbilder: Maus an eine feste Stelle des Fensters setzen, damit Hover-Knoepfe sichtbar werden */
   public GlassScreen demoHover(int x, int y) {
      this.demoMouse = new int[]{x, y};
      return this;
   }

   /** Nur fuer Testbilder: Person ohne bekannten Chat anwaehlen */
   public GlassScreen demoFriend(String userId) {
      this.friendWithoutChat = userId;
      return this;
   }

   /* ================= Masse ================= */

   private int pw() { return Math.min(600, this.width - 20); }
   private int ph() { return Math.min(360, this.height - 20); }
   private int px() { return (this.width - pw()) / 2; }
   private int py() { return (this.height - ph()) / 2; }
   private int cx() { return px() + 8; }
   private int cy() { return py() + 40; }
   private int cw() { return pw() - 16; }
   private int ch() { return ph() - 48; }
   private int listW() { return Mth.clamp(cw() * 2 / 5, 130, 220); }

   @Override
   protected void init() {
      this.searchBox = this.idBox = this.secretBox = null;
      GlassConfig cfg = GlassConfig.get();
      if (this.page != GlassScreen.Page.SETTINGS) {
         this.searchBox = this.box(this.filter, "Suchen", 48, v -> {
            this.filter = v;
            this.textListScroll = 0;
            this.listScroll = 0;
         });
         if (DiscordClient.INSTANCE.isConnected()) {
            DiscordClient.INSTANCE.refreshGuilds();
            String open = this.page == GlassScreen.Page.TEXT ? this.textGuild : this.selectedGuild;
            if (open != null) {
               DiscordClient.INSTANCE.loadChannels(open);
            }
         }
      } else if (cfg.useOwnApp) {
         this.idBox = this.box(cfg.clientId, "Application ID", 256, v -> {
            cfg.clientId = v.trim();
            GlassConfig.save();
         });
         this.secretBox = this.box(cfg.clientSecret, "Client Secret", 256, v -> {
            cfg.clientSecret = v.trim();
            GlassConfig.save();
         });
         EditBox secret = this.secretBox;
         secret.addFormatter((s, offset) -> FormattedCharSequence.forward(secret.isFocused() ? s : "•".repeat(s.length()), net.minecraft.network.chat.Style.EMPTY));
      }
   }

   /** Eingabefeld ohne Rahmen; Lage und Sichtbarkeit setzt das Zeichnen */
   private EditBox box(String value, String hint, int max, Consumer<String> onChange) {
      EditBox box = new EditBox(this.font, 0, 0, 80, 10, Component.literal(hint));
      box.setBordered(false);
      box.setMaxLength(max);
      box.setValue(value);
      // Platzhalter in der DeuClient-Schrift; selbst Getipptes bleibt wie ueberall in Minecraft
      box.setHint(Component.literal(eu.deusmp.deucore.Tr.t(hint)).withStyle(s -> s.withColor(0x8B909A).withoutShadow().withFont(Ui.uiFont())));
      box.setTextColor(Ui.TEXT);
      box.setResponder(onChange);
      box.visible = false;
      return this.addRenderableWidget(box);
   }

   private void drawField(GuiGraphicsExtractor g, EditBox box, int x, int y, int w) {
      DeuRender.roundRect(g, x, y, w, 16, 4, box.isFocused() ? 0xFF353A43 : 0xFF2A2E35);
      box.setX(x + 6);
      box.setY(y + 4);
      box.setWidth(w - 12);
      box.setTextShadow(!box.getValue().isEmpty()); // der Platzhalter ohne Schatten wie die uebrige Schrift
      box.visible = true;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      GlassConfig.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
      super.extractBackground(g, mx, my, delta);
      g.fillGradient(0, 0, this.width, this.height, 0x8007080A, 0xA007080A);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
      this.hits.clear();
      this.scrolls.clear();
      this.tip = null;
      if (this.demoMouse != null) {
         mx = px() + this.demoMouse[0];
         my = py() + this.demoMouse[1];
      }

      for (EditBox b : new EditBox[]{this.searchBox, this.idBox, this.secretBox}) {
         if (b != null) b.visible = false;
      }

      DiscordState s = DiscordClient.INSTANCE.state;
      DeuRender.roundRect(g, px(), py(), pw(), ph(), 8, Ui.BG);
      this.header(g, mx, my, s);
      if (this.page == GlassScreen.Page.SETTINGS) this.settingsTab(g, mx, my);
      else if (s.status != DiscordState.Status.CONNECTED) this.offline(g, mx, my, s);
      else if (this.page == GlassScreen.Page.TEXT) this.textTab(g, mx, my, s);
      else this.callTab(g, mx, my, s);
      super.extractRenderState(g, mx, my, delta);
      if (this.tip != null) Ui.tooltip(g, mx, my, this.tip, 170, this.width, this.height);
   }

   /* ================= Kopfzeile ================= */

   private void header(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int x = px() + 8, y = py() + 8;
      DeuRender.roundRect(g, x, y, 24, 24, 6, Ui.CARD);
      Ui.iconCentered(g, Ui.I_CHAT, x + 12, y + 12, 14, Ui.GOLD);

      int right = px() + pw() - 8;
      this.button(g, mx, my, right - 20, y + 2, 20, 20, Ui.I_CLOSE, "", Look.GHOST, true, this::onClose);
      right -= 24;
      boolean running = DiscordClient.INSTANCE.isRunning();
      this.button(g, mx, my, right - 20, y + 2, 20, 20, Ui.I_POWER, "", running ? Look.ACCENT : Look.NORMAL, true, () -> {
         if (DiscordClient.INSTANCE.isRunning()) DiscordClient.INSTANCE.disconnect();
         else DiscordClient.INSTANCE.connect();
      });
      right -= 28;
      String[] labels = {"Call", "Text", "Einstellungen"};
      for (int i = labels.length - 1; i >= 0; i--) {
         GlassScreen.Page target = GlassScreen.Page.values()[i];
         int bw = Ui.boldWidth(labels[i]) + 16;
         this.button(g, mx, my, right - bw, y + 2, bw, 20, null, labels[i], this.page == target ? Look.ACCENT : Look.NORMAL, true, () -> this.show(target));
         right -= bw + 4;
      }

      int tx = x + 31, room = right - tx - 2;
      Ui.boldText(g, "Discord", tx, y + 3, Ui.TEXT);
      DeuRender.roundRect(g, tx, y + 17, 5, 5, 2, s.status.color);
      String state = s.status == DiscordState.Status.CONNECTED && !s.userName.isEmpty() ? s.userName : eu.deusmp.deucore.Tr.t(s.status.label);
      this.userText(g, state, tx + 9, y + 15, room - 9, Ui.TEXT_DIM);
   }

   private void show(GlassScreen.Page target) {
      if (this.page != target) {
         this.page = target;
         lastPage = target;
         this.capturing = null;
         this.rebuildWidgets();
      }
   }

   /* ================= Nicht verbunden ================= */

   private void offline(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int x = cx(), y = cy(), w = cw(), h = ch();
      DeuRender.roundRect(g, x, y, w, h, 6, Ui.CARD);
      String title = switch (s.status) {
         case AUTHORIZING -> "Bestätige in Discord";
         case CONNECTING -> "Verbinde mit Discord…";
         case ERROR -> "Verbindung fehlgeschlagen";
         case WAITING -> "Warte auf Discord…";
         default -> "Nicht verbunden";
      };
      String detail = !s.statusDetail.isEmpty() ? s.statusDetail
            : s.status == DiscordState.Status.AUTHORIZING ? "Klicke im Discord-Fenster auf \"Autorisieren\"."
            : "Öffne die Discord-App auf diesem PC, mehr ist nicht nötig.";
      int mid = y + h / 2;
      Ui.iconCentered(g, "mod_headphones", x + w / 2, mid - 40, 28, Ui.TEXT_DIM);
      Ui.centered(g, title, x + w / 2, mid - 16, Ui.TEXT);
      int ly = mid - 2;
      for (FormattedCharSequence line : this.font.split(FormattedText.of(eu.deusmp.deucore.Tr.t(detail), net.minecraft.network.chat.Style.EMPTY.withFont(Ui.uiFont())), w - 60)) {
         g.text(this.font, line, x + w / 2 - this.font.width(line) / 2, ly, Ui.TEXT_DIM, false);
         ly += 10;
      }

      if (!DiscordClient.INSTANCE.isRunning()) {
         this.button(g, mx, my, x + w / 2 - 55, Math.min(ly + 8, y + h - 26), 110, 20, null, "Verbinden", Look.ACCENT, true, DiscordClient.INSTANCE::connect);
      }
   }

   /* ================= Reiter: Call ================= */

   private static String norm(String s) {
      return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
   }

   private void callTab(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int x = cx(), y = cy(), h = ch(), lw = listW();
      DeuRender.roundRect(g, x, y, lw, h, 6, Ui.CARD);
      DiscordState.Guild guild = s.guilds.stream().filter(it -> it.id().equals(this.selectedGuild)).findFirst().orElse(null);
      if (guild == null) this.overview(g, mx, my, s, false, x, y, lw, h);
      else this.channelList(g, mx, my, s, guild, x, y, lw, h);
      this.callPanel(g, mx, my, s, x + lw + 6, y, cw() - lw - 6, h);
   }

   /** Zeile der linken Liste: Ueberschrift, Hinweiszeile, Freund oder Server */
   private record Entry(String heading, FormattedCharSequence hint, GlassConfig.Friend friend, DiscordState.Guild guild) {
   }

   /** Freunde und Server, in beiden Reitern gleich aufgebaut; ein Klick auf einen Freund ruft an bzw. oeffnet den Chat */
   private void overview(GuiGraphicsExtractor g, int mx, int my, DiscordState s, boolean text, int x, int y, int w, int h) {
      if (this.searchBox != null) {
         this.drawField(g, this.searchBox, x + 4, y + 4, w - 8);
      }

      String q = norm(this.filter);
      List<Entry> entries = new ArrayList<>();
      List<GlassConfig.Friend> friends = GlassConfig.get().friends.stream().filter(f -> q.isEmpty() || norm(f.name).contains(q)).toList();
      List<DiscordState.Guild> guilds = s.guilds.stream().filter(it -> q.isEmpty() || norm(it.name()).contains(q)).toList();
      if (!friends.isEmpty() || q.isEmpty()) {
         entries.add(new Entry("Freunde", null, null, null));
         friends.forEach(f -> entries.add(new Entry(null, null, f, null)));
         if (friends.isEmpty()) {
            String hint = eu.deusmp.deucore.Tr.t("Noch keine. Klicke bei Personen im Call oder Chat auf den Stern.");
            this.wrap(hint, w - 18).forEach(line -> entries.add(new Entry(null, line, null, null)));
         }
      }

      if (!guilds.isEmpty()) {
         entries.add(new Entry("Server", null, null, null));
         guilds.forEach(it -> entries.add(new Entry(null, null, null, it)));
      }

      int top = y + 24, visible = Math.max(1, (y + h - 2 - top) / ROW);
      int offset = Mth.clamp(text ? this.textListScroll : this.listScroll, 0, Math.max(0, entries.size() - visible));
      if (text) this.textListScroll = offset;
      else this.listScroll = offset;
      this.scrolls.add(new Scroll(x, y, w, h, d -> {
         if (text) this.textListScroll += d;
         else this.listScroll += d;
      }));
      if (entries.isEmpty()) {
         Ui.centered(g, s.guilds.isEmpty() ? "Lade Server…" : "Kein Treffer", x + w / 2, top + 8, Ui.TEXT_DIM);
      }

      for (int i = offset; i < Math.min(entries.size(), offset + visible); i++) {
         Entry e = entries.get(i);
         int ry = top + (i - offset) * ROW;
         if (e.heading() != null) {
            Ui.text(g, e.heading(), x + 8, ry + 7, Ui.TEXT_DIM);
         } else if (e.hint() != null) {
            g.text(this.font, e.hint(), x + 8, ry + 5, Ui.TEXT_DIM, false);
         } else if (e.friend() != null) {
            this.friendRow(g, mx, my, s, e.friend(), text, x, ry, w);
         } else {
            DiscordState.Guild it = e.guild();
            boolean hover = in(mx, my, x + 4, ry, w - 10, ROW - 2);
            if (hover) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, Ui.CARD_HOVER);
            this.guildIcon(g, x + 8, ry + 2, it);
            this.userText(g, it.name(), x + 27, ry + 5, w - 36, Ui.TEXT);
            this.hit(x + 4, ry, w - 10, ROW - 2, () -> {
               if (text) {
                  this.textGuild = it.id();
                  this.textListScroll = 0;
               } else {
                  this.selectedGuild = it.id();
                  this.listScroll = 0;
               }

               DiscordClient.INSTANCE.loadChannels(it.id());
            });
         }
      }

      this.scrollbar(g, x + w - 4, top, visible * ROW - 2, entries.size(), visible, offset);
   }

   private void friendRow(GuiGraphicsExtractor g, int mx, int my, DiscordState s, GlassConfig.Friend f, boolean text, int x, int ry, int w) {
      boolean hover = in(mx, my, x + 4, ry, w - 10, ROW - 2);
      boolean active = text ? f.hasChat() ? f.id.equals(s.textChannelId) : s.textChannelId == null && f.userId.equals(this.friendWithoutChat)
            : f.hasChat() && f.id.equals(s.channelId);
      if (active) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, 0x402EFF7A);
      else if (hover) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, Ui.CARD_HOVER);
      Glass.avatar(g, this.font, x + 8, ry + 2, 14, f.userId.isEmpty() ? f.id : f.userId, f.name, false, f.icon);
      this.userText(g, f.name, x + 27, ry + 5, w - (hover ? 72 : 36), active ? Ui.GOLD : Ui.TEXT);

      Runnable call = f.hasChat() ? () -> this.call(f.id) : () -> DiscordClient.INSTANCE.openProfile(f.userId);
      this.hit(x + 4, ry, w - 10, ROW - 2, !text ? call : f.hasChat() ? () -> this.openChat(f.id, f.name, null) : () -> {
         DiscordClient.INSTANCE.closeText();
         this.friendWithoutChat = f.userId;
      });
      if (hover) { // nach der Zeile angemeldet, haben deshalb Vorrang
         this.smallButton(g, mx, my, x + w - 42, ry + 1, f.hasChat() ? "mod_call" : Ui.I_PERSON, Ui.TEXT,
               f.hasChat() ? "Anrufen" : "Profil in Discord öffnen", call);
         this.smallButton(g, mx, my, x + w - 24, ry + 1, Ui.I_CLOSE, Ui.TEXT_DIM, "Aus Freunden entfernen",
               () -> GlassConfig.get().removeFriend(f));
      }
   }

   private void smallButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, String icon, int color, String help, Runnable action) {
      boolean hover = in(mx, my, x, y, 16, 16);
      DeuRender.roundRect(g, x, y, 16, 16, 4, hover ? 0xFF353A43 : 0xFF2A2E35);
      Ui.iconCentered(g, icon, x + 8, y + 8, 10, color);
      if (hover) this.tip = help;
      this.hit(x, y, 16, 16, action);
   }

   /** Stern: Person aus Call oder Chat in die Freundesliste aufnehmen oder wieder entfernen */
   private void star(GuiGraphicsExtractor g, int mx, int my, int x, int y, String userId, String name, String avatar) {
      GlassConfig cfg = GlassConfig.get();
      GlassConfig.Friend known = cfg.friendByUser(userId);
      this.smallButton(g, mx, my, x, y, Ui.I_STAR, known != null ? Ui.GOLD : Ui.TEXT_DIM,
            known != null ? "Aus Freunden entfernen" : "Als Freund merken", () -> {
               if (known != null) cfg.removeFriend(known);
               else cfg.rememberFriend("", userId, name, avatar, true);
            });
   }

   /** Kopfzeile einer Kanalliste: zurueck zur Serverliste */
   private void backRow(GuiGraphicsExtractor g, int mx, int my, DiscordState.Guild guild, int x, int y, int w, Runnable action) {
      boolean back = in(mx, my, x + 4, y + 4, w - 8, 16);
      DeuRender.roundRect(g, x + 4, y + 4, w - 8, 16, 4, back ? 0xFF353A43 : 0xFF2A2E35);
      Ui.icon(g, Ui.I_CHEV_L, x + 9, y + 8, 8, Ui.TEXT_DIM);
      this.userText(g, guild.name(), x + 22, y + 8, w - 30, Ui.TEXT);
      this.hit(x + 4, y + 4, w - 8, 16, action);
   }

   private void channelList(GuiGraphicsExtractor g, int mx, int my, DiscordState s, DiscordState.Guild guild, int x, int y, int w, int h) {
      this.backRow(g, mx, my, guild, x, y, w, () -> {
         this.selectedGuild = null;
         this.listScroll = 0;
      });

      List<DiscordState.Channel> channels = s.channelsByGuild.get(guild.id());
      int top = y + 24, visible = Math.max(1, (y + h - 2 - top) / ROW);
      if (channels == null || channels.isEmpty()) {
         Ui.centered(g, channels == null ? "Lade Kanäle…" : "Keine Sprachkanäle", x + w / 2, top + 8, Ui.TEXT_DIM);
         return;
      }

      this.listScroll = Mth.clamp(this.listScroll, 0, Math.max(0, channels.size() - visible));
      this.scrolls.add(new Scroll(x, y, w, h, d -> this.listScroll += d));
      for (int i = this.listScroll; i < Math.min(channels.size(), this.listScroll + visible); i++) {
         DiscordState.Channel c = channels.get(i);
         int ry = top + (i - this.listScroll) * ROW;
         boolean current = c.id().equals(s.channelId);
         boolean hover = in(mx, my, x + 4, ry, w - 10, ROW - 2);
         if (current) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, 0x402EFF7A);
         else if (hover) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, Ui.CARD_HOVER);
         Ui.icon(g, "mod_volume", x + 9, ry + 4, 10, current ? Ui.GOLD : Ui.TEXT_DIM);
         int room = w - 34;
         if (hover && !current) {
            int jw = Ui.width("Beitreten");
            Ui.text(g, "Beitreten", x + w - 10 - jw, ry + 5, Ui.GOLD);
            room -= jw + 6;
         }

         this.userText(g, c.name(), x + 24, ry + 5, room, current ? Ui.GOLD : Ui.TEXT);
         if (!current) {
            this.hit(x + 4, ry, w - 10, ROW - 2, () -> DiscordClient.INSTANCE.joinVoice(c.id()));
         }
      }

      this.scrollbar(g, x + w - 4, top, visible * ROW - 2, channels.size(), visible, this.listScroll);
   }

   private void callPanel(GuiGraphicsExtractor g, int mx, int my, DiscordState s, int x, int y, int w, int h) {
      DiscordClient dc = DiscordClient.INSTANCE;
      DeuRender.roundRect(g, x, y, w, h, 6, Ui.CARD);
      int bottom = y + h - 26;
      if (!s.inCall()) {
         int mid = y + (bottom - y) / 2;
         Ui.iconCentered(g, "mod_volume", x + w / 2, mid - 18, 22, Ui.TEXT_DIM);
         Ui.centered(g, "Kein Sprachkanal", x + w / 2, mid, Ui.TEXT);
         Ui.centered(g, Ui.ellipsize("Wähle links einen Kanal.", w - 12), x + w / 2, mid + 12, Ui.TEXT_DIM);
      } else {
         String ping = s.ping >= 0 ? Math.round(s.ping) + " ms" : "";
         String link = s.voiceConnection.isEmpty() || s.voiceConnection.equals("VOICE_CONNECTED") ? ""
               : s.voiceConnection.replace("VOICE_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
         String info = link.isEmpty() ? ping : link;
         int iw = info.isEmpty() ? 0 : Ui.width(info);
         this.userText(g, s.channelName == null ? "" : s.channelName, x + 8, y + 6, w - 22 - iw, Ui.TEXT);
         Ui.text(g, info, x + w - 8 - iw, y + 6, link.isEmpty() ? Ui.GOLD : 0xFFFFC14D);
         this.userText(g, s.guildName == null ? "" : s.guildName, x + 8, y + 17, w - 16, Ui.TEXT_DIM);

         List<DiscordState.Member> members = s.members();
         int top = y + 31, visible = Math.max(1, (bottom - 3 - top) / ROW);
         this.memberScroll = Mth.clamp(this.memberScroll, 0, Math.max(0, members.size() - visible));
         this.scrolls.add(new Scroll(x, y, w, h, d -> this.memberScroll += d));
         for (int i = this.memberScroll; i < Math.min(members.size(), this.memberScroll + visible); i++) {
            DiscordState.Member m = members.get(i);
            int ry = top + (i - this.memberScroll) * ROW;
            if (m.speaking) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, 0x262EFF7A);
            Glass.avatar(g, this.font, x + 9, ry + 2, 14, m.id, m.name, m.speaking, m.avatar);
            int rx = x + w - 10;
            if (!m.id.equals(s.userId) && in(mx, my, x + 4, ry, w - 10, ROW - 2)) {
               this.star(g, mx, my, rx - 17, ry + 1, m.id, m.name, m.avatar);
               rx -= 21;
            }

            if (m.deafened()) rx = this.offIcon(g, "mod_headphones-off", rx, ry + 3);
            if (m.muted()) rx = this.offIcon(g, "mod_mic-off", rx, ry + 3);
            String name = m.id.equals(s.userId) ? m.name + " (" + eu.deusmp.deucore.Tr.t("du") + ")" : m.name;
            this.userText(g, name, x + 29, ry + 5, rx - (x + 29) - 4, m.muted() || m.deafened() ? Ui.TEXT_DIM : Ui.TEXT);
         }

         this.scrollbar(g, x + w - 4, top, visible * ROW - 2, members.size(), visible, this.memberScroll);
      }

      // Steuerleiste
      int bw = (w - 16) / 3, by = y + h - 22;
      this.control(g, mx, my, x + 4, by, bw, s.selfMute ? "mod_mic-off" : "mod_mic", s.selfMute ? "Stumm" : "Mikrofon", s.selfMute ? Look.DANGER : Look.NORMAL, true, dc::toggleMute);
      this.control(g, mx, my, x + 8 + bw, by, bw, s.selfDeaf ? "mod_headphones-off" : "mod_headphones", s.selfDeaf ? "Taub" : "Ton", s.selfDeaf ? Look.DANGER : Look.NORMAL, true, dc::toggleDeafen);
      this.control(g, mx, my, x + 12 + 2 * bw, by, w - 16 - 2 * bw, "mod_hangup", "Auflegen", Look.DANGER, s.inCall(), dc::leaveVoice);
   }

   /* ================= Reiter: Text ================= */

   private void textTab(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int x = cx(), y = cy(), h = ch(), lw = listW();
      DeuRender.roundRect(g, x, y, lw, h, 6, Ui.CARD);
      DiscordState.Guild guild = s.guilds.stream().filter(it -> it.id().equals(this.textGuild)).findFirst().orElse(null);
      if (guild == null) this.overview(g, mx, my, s, true, x, y, lw, h);
      else this.textChannels(g, mx, my, s, guild, x, y, lw, h);
      this.messagePanel(g, mx, my, s, x + lw + 6, y, cw() - lw - 6, h);
   }

   private void textChannels(GuiGraphicsExtractor g, int mx, int my, DiscordState s, DiscordState.Guild guild, int x, int y, int w, int h) {
      this.backRow(g, mx, my, guild, x, y, w, () -> {
         this.textGuild = null;
         this.textListScroll = 0;
      });
      List<DiscordState.Channel> channels = s.textByGuild.get(guild.id());
      int top = y + 24, visible = Math.max(1, (y + h - 2 - top) / ROW);
      if (channels == null || channels.isEmpty()) {
         Ui.centered(g, channels == null ? "Lade Kanäle…" : "Keine Textkanäle", x + w / 2, top + 8, Ui.TEXT_DIM);
         return;
      }

      this.textListScroll = Mth.clamp(this.textListScroll, 0, Math.max(0, channels.size() - visible));
      this.scrolls.add(new Scroll(x, y, w, h, d -> this.textListScroll += d));
      for (int i = this.textListScroll; i < Math.min(channels.size(), this.textListScroll + visible); i++) {
         DiscordState.Channel c = channels.get(i);
         int ry = top + (i - this.textListScroll) * ROW;
         boolean open = c.id().equals(s.textChannelId);
         boolean hover = in(mx, my, x + 4, ry, w - 10, ROW - 2);
         if (open) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, 0x402EFF7A);
         else if (hover) DeuRender.roundRect(g, x + 4, ry, w - 10, ROW - 2, 4, Ui.CARD_HOVER);
         Ui.text(g, "#", x + 11, ry + 5, open ? Ui.GOLD : Ui.TEXT_DIM);
         this.userText(g, c.name(), x + 24, ry + 5, w - 34, open ? Ui.GOLD : Ui.TEXT);
         this.hit(x + 4, ry, w - 10, ROW - 2, () -> this.openChat(c.id(), c.name(), guild.id()));
      }

      this.scrollbar(g, x + w - 4, top, visible * ROW - 2, channels.size(), visible, this.textListScroll);
   }

   private void openChat(String id, String name, String guild) {
      this.messageScroll = 0;
      this.friendWithoutChat = null;
      DiscordClient.INSTANCE.openText(id, name, guild);
   }

   /** Anruf in einer Direktnachricht: dem Sprachkanal der Unterhaltung beitreten */
   private void call(String channelId) {
      DiscordClient.INSTANCE.joinVoice(channelId);
      this.show(GlassScreen.Page.CALL);
   }

   private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
   private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm");

   private static String time(long millis) {
      java.time.ZonedDateTime t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault());
      return (t.toLocalDate().equals(java.time.LocalDate.now()) ? TIME : DAY).format(t);
   }

   private void messagePanel(GuiGraphicsExtractor g, int mx, int my, DiscordState s, int x, int y, int w, int h) {
      DeuRender.roundRect(g, x, y, w, h, 6, Ui.CARD);
      String channel = s.textChannelId;
      GlassConfig.Friend waiting = this.friendWithoutChat == null ? null : GlassConfig.get().friendByUser(this.friendWithoutChat);
      if (channel == null && waiting != null) {
         if (waiting.hasChat()) { // inzwischen kam eine Nachricht oder ein Anruf
            this.openChat(waiting.id, waiting.name, null);
         } else {
            this.noChat(g, mx, my, waiting, x, y, w, h);
         }

         return;
      }

      if (channel == null) {
         Ui.centered(g, Ui.ellipsize("Wähle links einen Kanal.", w - 12), x + w / 2, y + h / 2 - 12, Ui.TEXT);
         int ly = y + h / 2 + 2;
         for (FormattedCharSequence line : this.wrap(eu.deusmp.deucore.Tr.t("Freunde und Server stehen links. Zum Schreiben öffnet sich die Discord-App."), w - 40)) {
            g.text(this.font, line, x + w / 2 - this.font.width(line) / 2, ly, Ui.TEXT_DIM, false);
            ly += 10;
         }

         return;
      }

      boolean direct = s.textGuildId == null;
      int room = w - 16;
      if (direct) {
         int bw = Ui.boldWidth("Anrufen") + 20;
         this.button(g, mx, my, x + w - 4 - bw, y + 4, bw, 16, null, "Anrufen", Look.RAISED, true, () -> this.call(channel));
         room -= bw;
      }

      this.userText(g, (direct ? "" : "# ") + s.textChannelName, x + 8, y + 8, room, Ui.TEXT);
      this.button(g, mx, my, x + 4, y + h - 22, w - 8, 18, null, "In Discord schreiben", Look.ACCENT, true,
            () -> DiscordClient.INSTANCE.openInDiscord(channel, s.textGuildId));

      int top = y + 24, bottom = y + h - 26, view = bottom - top;
      List<DiscordState.Message> messages = s.messages;
      if (!s.textStatus.isEmpty() || messages.isEmpty()) {
         Ui.centered(g, Ui.ellipsize(s.textStatus.isEmpty() ? "Keine Nachrichten" : s.textStatus, w - 12), x + w / 2, top + view / 2 - 4, Ui.TEXT_DIM);
         return;
      }

      // aufeinanderfolgende Nachrichten derselben Person werden zusammengefasst
      List<DiscordState.Message> list = List.copyOf(messages);
      List<List<FormattedCharSequence>> lines = new ArrayList<>(list.size());
      boolean[] head = new boolean[list.size()];
      int total = 0;
      for (int i = 0; i < list.size(); i++) {
         DiscordState.Message m = list.get(i);
         head[i] = i == 0 || !m.authorId().equals(list.get(i - 1).authorId()) || m.time() - list.get(i - 1).time() > 300_000L;
         lines.add(this.wrap(DiscordState.readableText(m.text()), w - 36));
         total += (head[i] ? 15 : 1) + lines.get(i).size() * 10;
      }

      int max = Math.max(0, total - view);
      this.messageScroll = Mth.clamp(this.messageScroll, 0, max);
      this.scrolls.add(new Scroll(x, top, w, view, d -> this.messageScroll -= d * 20));
      int ly = max == 0 ? top : bottom - total + this.messageScroll;
      g.enableScissor(x + 2, top, x + w - 2, bottom);
      for (int i = 0; i < list.size(); i++) {
         DiscordState.Message m = list.get(i);
         int height = (head[i] ? 15 : 1) + lines.get(i).size() * 10;
         if (ly + height > top && ly < bottom) {
            int ty = ly + 1;
            if (head[i]) {
               ty = ly + 4;
               Glass.avatar(g, this.font, x + 8, ty, 14, m.authorId(), m.author(), false, m.avatar());
               String when = time(m.time());
               int tw = Ui.width(when);
               this.userText(g, m.author(), x + 28, ty, w - 44 - tw, Ui.GOLD);
               int nameW = Math.min(Ui.rawWidth(m.author()), w - 44 - tw);
               Ui.text(g, when, x + 32 + nameW, ty, Ui.TEXT_DIM);
               // Stern neben dem Namen, solange die Maus auf der Nachricht liegt und er ganz sichtbar ist
               if (!m.authorId().equals(s.userId) && ty - 4 >= top && ty + 12 <= bottom
                     && in(mx, my, x + 4, ly, w - 8, height)) {
                  this.star(g, mx, my, Math.min(x + w - 22, x + 36 + nameW + tw), ty - 4, m.authorId(), m.author(), m.avatar());
               }

               ty += 11;
            }

            for (FormattedCharSequence line : lines.get(i)) {
               g.text(this.font, line, x + 28, ty, Ui.TEXT, false);
               ty += 10;
            }
         }

         ly += height;
      }

      g.disableScissor();
      if (max > 0) {
         int bar = Math.max(12, view * view / total);
         DeuRender.roundRect(g, x + w - 3, top + (view - bar) * (max - this.messageScroll) / max, 2, bar, 1, 0x55FFFFFF);
      }
   }

   /** Rechte Seite fuer eine Person, zu der noch kein Chat bekannt ist */
   private void noChat(GuiGraphicsExtractor g, int mx, int my, GlassConfig.Friend f, int x, int y, int w, int h) {
      int mid = y + h / 2;
      Glass.avatar(g, this.font, x + w / 2 - 14, mid - 52, 28, f.userId, f.name, false, f.icon);
      int nameW = Math.min(Ui.rawWidth(f.name), w - 16);
      this.userText(g, f.name, x + (w - nameW) / 2, mid - 18, w - 16, Ui.TEXT);
      int ly = mid - 4;
      for (FormattedCharSequence line : this.wrap(eu.deusmp.deucore.Tr.t("Der Chat erscheint hier, sobald eine Nachricht oder ein Anruf von dieser Person kommt."), w - 40)) {
         g.text(this.font, line, x + w / 2 - this.font.width(line) / 2, ly, Ui.TEXT_DIM, false);
         ly += 10;
      }

      this.button(g, mx, my, x + 4, y + h - 22, w - 8, 18, null, "Profil in Discord öffnen", Look.ACCENT, true,
            () -> DiscordClient.INSTANCE.openProfile(f.userId));
   }

   private List<FormattedCharSequence> wrap(String text, int width) {
      return this.font.split(FormattedText.of(text, net.minecraft.network.chat.Style.EMPTY.withFont(Ui.uiFont())), width);
   }

   /* ================= Reiter: Einstellungen ================= */

   private void settingsTab(GuiGraphicsExtractor g, int mx, int my) {
      GlassConfig cfg = GlassConfig.get();
      int y = cy(), h = ch(), w = (cw() - 6) / 2;
      int lx = cx(), rx = cx() + w + 6;
      DeuRender.roundRect(g, lx, y, w, h, 6, Ui.CARD);
      DeuRender.roundRect(g, rx, y, cw() - w - 6, h, 6, Ui.CARD);

      // links: Overlay und Verbindung
      Column left = new Column(g, mx, my, lx, y, w, h, this.leftScroll);
      left.heading("Overlay");
      left.toggle("Call-HUD anzeigen", cfg.hudEnabled, () -> cfg.hudEnabled = !cfg.hudEnabled);
      left.toggle("Nur sprechende Mitglieder", cfg.hudOnlySpeaking, () -> cfg.hudOnlySpeaking = !cfg.hudOnlySpeaking);
      left.toggle("Benachrichtigungen", cfg.notifications, () -> cfg.notifications = !cfg.notifications);
      String[] corners = {"Oben links", "Oben rechts", "Unten links", "Unten rechts"};
      left.value("Position", corners[Math.floorMod(cfg.hudCorner, 4)], () -> cfg.hudCorner = (cfg.hudCorner + 1) % 4);
      left.action("Größe und Hintergrund", this::openAppearance);
      left.heading("Verbindung");
      left.toggle("Automatisch verbinden", cfg.autoConnect, () -> cfg.autoConnect = !cfg.autoConnect);
      left.toggle("Eigene Discord-App", cfg.useOwnApp, () -> {
         cfg.useOwnApp = !cfg.useOwnApp;
         GlassConfig.save();
         this.rebuildWidgets();
         DiscordClient.INSTANCE.disconnect();
         DiscordClient.INSTANCE.connect();
      });
      if (cfg.useOwnApp) {
         left.field(this.idBox);
         left.field(this.secretBox);
         left.hint("Redirect: http://localhost");
         left.toggle("Rich Presence", cfg.richPresence, () -> cfg.richPresence = !cfg.richPresence);
      }

      left.action("Anmeldung zurücksetzen", DiscordClient.INSTANCE::forgetLogin);
      this.leftScroll = left.finish(d -> this.leftScroll += d);

      // rechts: Tasten
      Column right = new Column(g, mx, my, rx, y, cw() - w - 6, h, this.rightScroll);
      right.heading("Tasten");
      right.key("Menü öffnen", cfg.menuKey, false);
      right.key("Stummschalten", cfg.muteKey, true);
      right.key("Taubschalten", cfg.deafenKey, true);
      right.key("HUD ein/aus", cfg.hudKey, true);
      right.key("Call verlassen", cfg.leaveKey, true);
      right.hint("Rechtsklick entfernt eine Taste.");
      this.rightScroll = right.finish(d -> this.rightScroll += d);
   }

   private void openAppearance() {
      Features.all(p -> p).stream().filter(f -> f.id().equals("discordglass")).findFirst()
            .ifPresent(f -> this.minecraft.gui.setScreen(new FeatureSettingsScreen(this, f)));
   }

   /** Eine scrollbare Spalte der Einstellungen: Zeilen werden von oben nach unten angelegt */
   private final class Column {
      private final GuiGraphicsExtractor g;
      private final int mx, my, x, top, w, h, offset;
      private int y;

      Column(GuiGraphicsExtractor g, int mx, int my, int x, int top, int w, int h, int offset) {
         this.g = g;
         this.mx = mx;
         this.my = my;
         this.x = x;
         this.top = top;
         this.w = w;
         this.h = h;
         this.offset = offset;
         this.y = top + 5 - offset;
         g.enableScissor(x, top + 2, x + w, top + h - 2);
      }

      /** true, wenn die Zeile ganz sichtbar ist (nur dann ist sie anklickbar) */
      private boolean inside(int height) {
         return this.y >= this.top + 1 && this.y + height <= this.top + this.h - 1;
      }

      private boolean row(int height) {
         boolean hover = this.inside(height) && in(this.mx, this.my, this.x + 4, this.y, this.w - 8, height);
         DeuRender.roundRect(this.g, this.x + 4, this.y, this.w - 8, height, 4, hover ? Ui.FIELD_HOVER : Ui.FIELD);
         return hover;
      }

      private void click(int height, Runnable action) {
         if (this.inside(height)) {
            GlassScreen.this.hit(this.x + 4, this.y, this.w - 8, height, () -> {
               action.run();
               GlassConfig.save();
            });
         }
      }

      void heading(String text) {
         Ui.headingText(this.g, text, this.x + 8, this.y + 2, Ui.TEXT);
         this.y += 15;
      }

      void toggle(String label, boolean on, Runnable flip) {
         this.row(18);
         Ui.text(this.g, Ui.ellipsize(label, this.w - 48), this.x + 10, this.y + 5, Ui.TEXT);
         int tx = this.x + this.w - 32;
         DeuRender.roundRect(this.g, tx, this.y + 4, 22, 10, 5, on ? Ui.ACCENT : 0xFF3A3F48);
         DeuRender.roundRect(this.g, on ? tx + 13 : tx + 1, this.y + 5, 8, 8, 4, on ? 0xFF0A0D0F : 0xFFB6BCC6);
         this.click(18, flip);
         this.y += 20;
      }

      void value(String label, String value, Runnable next) {
         this.row(18);
         int vw = Ui.width(value);
         Ui.text(this.g, Ui.ellipsize(label, this.w - 30 - vw), this.x + 10, this.y + 5, Ui.TEXT);
         Ui.text(this.g, value, this.x + this.w - 10 - vw, this.y + 5, Ui.GOLD);
         this.click(18, next);
         this.y += 20;
      }

      void action(String label, Runnable action) {
         boolean hover = this.inside(18) && in(this.mx, this.my, this.x + 4, this.y, this.w - 8, 18);
         DeuRender.roundRect(this.g, this.x + 4, this.y, this.w - 8, 18, 4, hover ? 0xFF353A43 : 0xFF2A2E35);
         Ui.centered(this.g, Ui.ellipsize(label, this.w - 16), this.x + this.w / 2, this.y + 5, Ui.TEXT);
         this.click(18, action);
         this.y += 20;
      }

      void field(EditBox box) {
         if (box != null && this.inside(16)) {
            GlassScreen.this.drawField(this.g, box, this.x + 4, this.y, this.w - 8);
         }

         this.y += 18;
      }

      void hint(String text) {
         Ui.text(this.g, Ui.ellipsize(text, this.w - 16), this.x + 8, this.y + 2, Ui.TEXT_DIM);
         this.y += 14;
      }

      void key(String label, Hotkey key, boolean removable) {
         this.row(18);
         boolean cap = GlassScreen.this.capturing == key;
         String text = cap ? eu.deusmp.deucore.Tr.t("Taste drücken…") : key.describe();
         int kw = Math.max(54, Ui.width(text) + 12), kx = this.x + this.w - 6 - kw;
         Ui.text(this.g, Ui.ellipsize(label, kx - this.x - 14), this.x + 10, this.y + 5, Ui.TEXT);
         DeuRender.roundRect(this.g, kx, this.y + 2, kw, 14, 3, cap ? Ui.ACCENT : 0xFF2A2E35);
         Ui.centered(this.g, text, kx + kw / 2, this.y + 5, cap ? 0xFF0A0D0F : key.isBound() ? Ui.TEXT : Ui.TEXT_DIM);
         if (this.inside(18)) {
            GlassScreen.this.hits.add(new Hit(this.x + 4, this.y, this.w - 8, 18, () -> GlassScreen.this.capturing = cap ? null : key, () -> {
               if (removable) {
                  key.key = -1;
                  key.ctrl = key.shift = key.alt = false;
                  GlassConfig.save();
               }

               GlassScreen.this.capturing = null;
            }));
         }

         this.y += 20;
      }

      /** Schliesst die Spalte ab und liefert den auf den Inhalt begrenzten Scroll-Stand */
      int finish(Consumer<Integer> scrollBy) {
         this.g.disableScissor();
         int content = this.y + this.offset - this.top + 3;
         int max = Math.max(0, content - this.h);
         if (max > 0) {
            GlassScreen.this.scrolls.add(new Scroll(this.x, this.top, this.w, this.h, d -> scrollBy.accept(d * 20)));
            int bar = Math.max(16, (this.h - 8) * this.h / content);
            DeuRender.roundRect(this.g, this.x + this.w - 3, this.top + 4 + (this.h - 8 - bar) * Math.min(this.offset, max) / max, 2, bar, 1, 0x55FFFFFF);
         }

         return Mth.clamp(this.offset, 0, max);
      }
   }

   /* ================= Bausteine ================= */

   private enum Look { NORMAL, ACCENT, DANGER, GHOST, RAISED }

   private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String icon, String label, Look look, boolean active, Runnable action) {
      boolean hover = active && in(mx, my, x, y, w, h);
      int bg = switch (look) {
         case ACCENT -> hover ? Ui.ACCENT_HOVER : Ui.ACCENT;
         case DANGER -> hover ? 0x80B4303F : 0x33C0394B;
         case GHOST -> hover ? 0x16FFFFFF : 0;
         case RAISED -> hover ? 0xFF353A43 : 0xFF2A2E35; // hebt sich von der Karte ab
         default -> hover ? Ui.FIELD_HOVER : Ui.FIELD;
      };
      int color = !active ? 0xFF5C6270 : look == Look.ACCENT ? 0xFF0A0D0F : look == Look.DANGER ? RED : Ui.TEXT;
      if (!label.isEmpty()) {
         Ui.boldButton(g, label, x, y, w, h, 4, bg, color);
      } else {
         if (bg != 0) DeuRender.roundRect(g, x, y, w, h, 4, bg);
         if (icon != null) Ui.iconCentered(g, icon, x + w / 2, y + h / 2, 10, color);
      }

      if (active) this.hit(x, y, w, h, action);
   }

   /** Knopf der Steuerleiste: Symbol und, wenn Platz ist, Beschriftung */
   private void control(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, Look look, boolean active, Runnable action) {
      if (look == Look.NORMAL) DeuRender.roundRect(g, x, y, w, 18, 4, 0xFF2A2E35); // hebt sich von der Karte ab
      this.button(g, mx, my, x, y, w, 18, null, "", look == Look.NORMAL ? Look.GHOST : look, active, action);
      int color = !active ? 0xFF5C6270 : look == Look.DANGER ? RED : Ui.TEXT;
      int tw = Ui.width(label);
      boolean text = tw + 20 <= w;
      int sx = x + (w - (text ? 15 + tw : 10)) / 2;
      Ui.icon(g, icon, sx, y + 4, 10, color);
      if (text) Ui.text(g, label, sx + 15, y + 5, color);
   }

   /** Rotes, durchgestrichenes Symbol rechtsbuendig; liefert die neue rechte Kante */
   private int offIcon(GuiGraphicsExtractor g, String icon, int right, int y) {
      Ui.icon(g, icon, right - 10, y + 1, 10, RED);
      return right - 15;
   }

   private void guildIcon(GuiGraphicsExtractor g, int x, int y, DiscordState.Guild guild) {
      DiscordClient.INSTANCE.loadGuildIcon(guild.id());
      Glass.avatar(g, this.font, x, y, 14, guild.id(), guild.name(), false, DiscordClient.INSTANCE.state.guildIcons.get(guild.id()));
   }

   /** Namen aus Discord: nicht uebersetzen, nur auf die Breite kuerzen */
   private void userText(GuiGraphicsExtractor g, String text, int x, int y, int maxW, int color) {
      if (text == null || maxW <= 4) return;
      String shown = text;
      if (Ui.rawWidth(shown) > maxW) {
         while (shown.length() > 1 && Ui.rawWidth(shown + "…") > maxW) shown = shown.substring(0, shown.offsetByCodePoints(shown.length(), -1));
         shown += "…";
      }

      g.text(this.font, Ui.raw(shown), x, y, color, false);
   }

   private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int total, int visible, int offset) {
      if (total > visible) {
         int bar = Math.max(12, h * visible / total);
         DeuRender.roundRect(g, x, y + (h - bar) * offset / (total - visible), 2, bar, 1, 0x55FFFFFF);
      }
   }

   private void hit(int x, int y, int w, int h, Runnable action) {
      this.hits.add(new Hit(x, y, w, h, action, null));
   }

   private static boolean in(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   /* ================= Eingaben ================= */

   @Override
   public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
      if (super.mouseClicked(e, doubleClick)) return true;
      for (EditBox b : new EditBox[]{this.searchBox, this.idBox, this.secretBox}) {
         if (b != null) b.setFocused(false);
      }

      this.setFocused(null);
      for (int i = this.hits.size() - 1; i >= 0; i--) {
         Hit h = this.hits.get(i);
         if (in(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) {
            Runnable r = e.button() == InputConstants.MOUSE_BUTTON_LEFT ? h.left()
                  : e.button() == InputConstants.MOUSE_BUTTON_RIGHT ? h.right() : null;
            if (r != null) {
               AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
               r.run();
               return true;
            }
         }
      }

      this.capturing = null;
      return false;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      for (Scroll a : this.scrolls) {
         if (sy != 0 && in(mx, my, a.x(), a.y(), a.w(), a.h())) {
            a.by().accept(sy > 0 ? -1 : 1);
            return true;
         }
      }

      return super.mouseScrolled(mx, my, sx, sy);
   }

   @Override
   public boolean keyPressed(KeyEvent e) {
      if (this.capturing != null) {
         if (e.isEscape()) {
            this.capturing = null;
         } else if (!Hotkey.isModifierKey(e.key())) {
            this.capturing.key = e.key();
            this.capturing.ctrl = e.hasControlDown();
            this.capturing.shift = e.hasShiftDown();
            this.capturing.alt = e.hasAltDown();
            this.capturing = null;
            GlassConfig.save();
         }

         return true;
      }

      boolean typing = this.getFocused() instanceof EditBox;
      if (!typing && GlassConfig.get().menuKey.matches(e.key(), e.hasControlDown(), e.hasShiftDown(), e.hasAltDown())) {
         this.onClose();
         return true;
      }

      return super.keyPressed(e);
   }

   private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
   }

   private record Scroll(int x, int y, int w, int h, Consumer<Integer> by) {
   }
}
