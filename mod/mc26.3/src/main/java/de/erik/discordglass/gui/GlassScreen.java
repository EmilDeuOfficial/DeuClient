package de.erik.discordglass.gui;

import eu.deusmp.deucore.Fx;
import de.erik.discordglass.config.GlassConfig;
import de.erik.discordglass.config.Hotkey;
import de.erik.discordglass.discord.DiscordClient;
import de.erik.discordglass.discord.DiscordState;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class GlassScreen extends Screen {
   private static GlassScreen.Tab lastTab = GlassScreen.Tab.OVERVIEW;
   private final Screen parent;
   private GlassScreen.Tab tab = lastTab;
   private final List<GlassScreen.Hit> hits = new ArrayList<>();
   private final List<GlassScreen.Scroll> scrolls = new ArrayList<>();
   private final Map<String, Float> anims = new HashMap<>();
   private Hotkey capturing;
   private int memberScroll;
   private int guildScroll;
   private int channelScroll;
   private String selectedGuild;
   private String guildFilter = "";
   private String channelFilter = "";
   private EditBox guildBox;
   private EditBox channelBox;
   private long openedAt;
   private long lastFrame;
   private float dt;
   private float tabY = -1.0F;
   private int px;
   private int py;
   private int pw;
   private int ph;
   private EditBox idBox;
   private EditBox secretBox;
   private EditBox redirectBox;
   private double lastMouseX;
   private GlassScreen.Hit slider;

   public GlassScreen(Screen parent) {
      super(Component.literal("Discord Glass"));
      this.parent = parent;
   }

   public static GlassScreen demo(Screen parent, int tabIndex, String guild, boolean captureMute) {
      lastTab = GlassScreen.Tab.values()[tabIndex];
      GlassScreen sc = new GlassScreen(parent);
      sc.selectedGuild = guild;
      if (tabIndex == 1) {
         sc.guildFilter = "b";
      }

      if (captureMute) {
         sc.capturing = GlassConfig.get().muteKey;
      }

      return sc;
   }

   private int cx() {
      return this.px + 122;
   }

   private int cy() {
      return this.py + 40;
   }

   private int cw() {
      return this.pw - 122 - 10;
   }

   private int ch() {
      return this.ph - 40 - 10;
   }

   protected void init() {
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }

      this.pw = Math.min(450, this.width - 16);
      this.ph = Math.min(272, this.height - 16);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
      this.idBox = this.secretBox = this.redirectBox = null;
      this.guildBox = this.channelBox = null;
      if (this.tab == GlassScreen.Tab.SERVERS) {
         int top = this.cy() + 26;
         int lw = (this.cw() - 6) / 2;
         int kw = this.cw() - lw - 6;
         this.guildBox = this.searchField(this.cx() + 4, top + 4, lw - 8, this.guildFilter, "Server suchen…", v -> {
            this.guildFilter = v;
            this.guildScroll = 0;
         });
         this.channelBox = this.searchField(this.cx() + lw + 6 + 4, top + 4, kw - 8, this.channelFilter, "Kanal suchen…", v -> {
            this.channelFilter = v;
            this.channelScroll = 0;
         });
      }

      if (this.tab == GlassScreen.Tab.SETTINGS && GlassConfig.get().useOwnApp) {
         GlassConfig cfg = GlassConfig.get();
         int x = this.cx() + 62;
         int w = this.cw() - 62;
         this.idBox = this.field(x, this.cy() + 31, w, cfg.clientId, "Application ID", v -> cfg.clientId = v.trim());
         this.secretBox = this.field(x, this.cy() + 51, w, cfg.clientSecret, "Client Secret", v -> cfg.clientSecret = v.trim());
         EditBox sb = this.secretBox;
         sb.addFormatter((s, off) -> FormattedCharSequence.forward(sb.isFocused() ? s : "•".repeat(s.length()), Style.EMPTY));
      }

      if (this.tab == GlassScreen.Tab.SERVERS && DiscordClient.INSTANCE.isConnected()) {
         DiscordClient.INSTANCE.refreshGuilds();
      }
   }

   private EditBox field(int x, int y, int w, String value, String hint, Consumer<String> onChange) {
      EditBox box = new EditBox(this.font, x + 6, y + 4, w - 12, 10, Component.literal(hint));
      box.setBordered(false);
      box.setMaxLength(256);
      box.setValue(value);
      box.setHint(Component.literal(hint).withStyle(s -> s.withColor(6184562)));
      box.setTextColor(-657926);
      box.setResponder(v -> {
         onChange.accept(v);
         GlassConfig.save();
      });
      return (EditBox)this.addRenderableWidget(box);
   }

   private EditBox searchField(int x, int y, int w, String value, String hint, Consumer<String> onChange) {
      EditBox box = new EditBox(this.font, x + 16, y + 3, w - 16 - 14, 10, Component.literal(hint));
      box.setBordered(false);
      box.setMaxLength(48);
      box.setValue(value);
      box.setHint(Component.literal(hint).withStyle(s -> s.withColor(6184562)));
      box.setTextColor(-657926);
      box.setResponder(onChange);
      return (EditBox)this.addRenderableWidget(box);
   }

   private static String norm(String s) {
      return Normalizer.normalize(s == null ? "" : s, Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
   }

   private static <T> List<T> filter(List<T> all, String query, Function<T, String> name) {
      String q = norm(query);
      if (q.isEmpty()) {
         return all;
      } else {
         List<T> out = new ArrayList<>();

         for (T t : all) {
            if (norm(name.apply(t)).contains(q)) {
               out.add(t);
            }
         }

         out.sort((a, b) -> Boolean.compare(!norm(name.apply((T)a)).startsWith(q), !norm(name.apply((T)b)).startsWith(q)));
         return out;
      }
   }

   private void searchBar(GuiGraphicsExtractor g, int mx, int my, EditBox box, int x, int y, int w, String id) {
      boolean focus = box.isFocused();
      float f = this.anim("sf" + id, focus, 12.0F);
      if (f > 0.01F) {
         Glass.glow(g, x, y, w, 14, 5, 0xFF2EFF7A, 2, f);
      }

      Glass.roundRect(g, x, y, w, 14, 5, Glass.lerpColor(1342177280, -1879048192, f));
      Glass.roundOutline(g, x, y, w, 14, 5, Glass.lerpColor(620756991, 0xFF2EFF7A, f));
      Glass.icon(g, Glass.ICON_SEARCH, x + 5, y + 4, Glass.lerpColor(-10592654, 0xFF1FD35F, f));
      if (!box.getValue().isEmpty()) {
         int bx = x + w - 13;
         int by = y + 2;
         float hv = this.hover("sx" + id, in(mx, my, bx, by, 10, 10));
         Glass.roundRect(g, bx, by, 10, 10, 5, Glass.alpha(822083583, 0.4F + 0.6F * hv));
         Glass.icon(g, Glass.ICON_CLOSE, bx + 2, by + 2, Glass.lerpColor(-6184523, -1, hv));
         this.hit(bx, by, 10, 10, () -> box.setValue(""));
      }
   }

   private void switchTab(GlassScreen.Tab t) {
      if (t != this.tab) {
         this.tab = t;
         lastTab = t;
         this.capturing = null;
         this.rebuildWidgets();
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      GlassConfig.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   private float anim(String key, boolean target, float speed) {
      float v = this.anims.getOrDefault(key, target ? 1.0F : 0.0F);
      float t = target ? 1.0F : 0.0F;
      v += (t - v) * Math.min(1.0F, this.dt * speed);
      if (Math.abs(t - v) < 0.002F) {
         v = t;
      }

      this.anims.put(key, v);
      return v;
   }

   private float hover(String key, boolean hovered) {
      return this.anim("h:" + key, hovered, 14.0F);
   }

   public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
      super.extractBackground(g, mx, my, pt);
      float a = Math.min(1.0F, (float)(System.currentTimeMillis() - this.openedAt) / 220.0F);
      g.fillGradient(0, 0, this.width, this.height, Glass.alpha(-1342177280, a), Glass.alpha(-536870912, a));
      int gx = this.width / 2;
      int gy = this.height / 2;

      for (int i = 0; i < 6; i++) {
         int r = 60 + i * 30;
         Glass.roundRect(g, gx - r * 2, gy - r, r * 4, r * 2, r, Glass.withAlpha(0xFF2EFF7A, (int)(5.0F * a)));
      }
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
      long now = System.currentTimeMillis();
      this.dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.hits.clear();
      this.scrolls.clear();
      this.slider = null;
      float open = Math.min(1.0F, (float)(now - this.openedAt) / 260.0F);
      float ease = 1.0F - (float)Math.pow(1.0F - open, 3.0);
      g.pose().pushMatrix();
      g.pose().translate(0.0F, (1.0F - ease) * 10.0F);
      DiscordState s = DiscordClient.INSTANCE.state;
      Glass.glow(g, this.px, this.py, this.pw, this.ph, 12, 0xFF2EFF7A, 6, 0.55F * ease);
      Glass.panel(g, this.px, this.py, this.pw, this.ph, 12, 0.6F + 0.4F * ease);
      this.renderHeader(g, mx, my, s);
      this.renderSidebar(g, mx, my, s);
      switch (this.tab) {
         case OVERVIEW:
            this.renderOverview(g, mx, my, s);
            break;
         case SERVERS:
            this.renderServers(g, mx, my, s);
            break;
         case HOTKEYS:
            this.renderHotkeys(g, mx, my);
            break;
         case SETTINGS:
            this.renderSettings(g, mx, my);
      }

      super.extractRenderState(g, mx, my, pt);
      g.pose().popMatrix();
   }

   private void renderHeader(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int lx = this.px + 10;
      int ly = this.py + 8;
      Glass.glow(g, lx, ly, 20, 20, 6, 0xFF2EFF7A, 4, 0.8F);
      Glass.roundGradient(g, lx, ly, 20, 20, 6, 0xFF2EFF7A, -10983950);
      Glass.icon(g, Glass.ICON_HEADPHONES, lx + 6, ly + 5, -1);
      Fx.text(g, this.font, "Discord Glass", lx + 27, ly + 2, -657926, false);
      Fx.text(g, this.font, "für Minecraft 26.3", lx + 27, ly + 12, -10592654, false);
      int bx = this.px + this.pw - 26;
      int by = this.py + 9;
      float hc = this.hover("close", in(mx, my, bx, by, 18, 18));
      Glass.roundRect(g, bx, by, 18, 18, 6, Glass.lerpColor(285212671, Glass.withAlpha(-45730, 192), hc));
      Glass.icon(g, Glass.ICON_CLOSE, bx + 6, by + 6, Glass.lerpColor(-6184523, -1, hc));
      this.hit(bx, by, 18, 18, this::onClose);
      boolean authorizing = s.status == DiscordState.Status.AUTHORIZING || s.status == DiscordState.Status.CONNECTING;
      String label = s.status.label;
      int w = Fx.width(this.font, label) + 18;
      int sx = bx - 6 - w;
      Glass.roundRect(g, sx, by + 3, w, 12, 6, Glass.withAlpha(s.status.color, 38));
      Glass.roundOutline(g, sx, by + 3, w, 12, 6, Glass.withAlpha(s.status.color, 96));
      int dc = authorizing ? Glass.alpha(s.status.color, 0.4F + 0.6F * Glass.pulse(800L)) : s.status.color;
      if (s.status == DiscordState.Status.CONNECTED) {
         Glass.glow(g, sx + 5, by + 7, 4, 4, 2, s.status.color, 2, Glass.pulse(1600L));
      }

      Glass.dot(g, sx + 7, by + 9, 2, dc);
      Fx.text(g, this.font, label, sx + 13, by + 5, -657926, false);
      int lineY = this.py + 34;
      Glass.roundGradientH(g, this.px + 10, lineY, this.pw - 20, 1, 0, 16777215, 822083583);
   }

   private void renderSidebar(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int sx = this.px + 8;
      int sy = this.py + 42;
      int sw = 106;
      int itemH = 22;
      int target = sy + this.tab.ordinal() * (itemH + 3);
      if (this.tabY < 0.0F) {
         this.tabY = target;
      }

      this.tabY = this.tabY + (target - this.tabY) * Math.min(1.0F, this.dt * 16.0F);
      int ty = Math.round(this.tabY);
      Glass.roundGradientH(g, sx, ty, sw, itemH, 6, Glass.withAlpha(0xFF2EFF7A, 85), Glass.withAlpha(0xFF2EFF7A, 8));
      Glass.roundOutline(g, sx, ty, sw, itemH, 6, Glass.withAlpha(0xFF2EFF7A, 64));
      Glass.glow(g, sx, ty + 6, 2, 10, 1, 0xFF2EFF7A, 3, 1.0F);
      Glass.roundRect(g, sx, ty + 6, 2, 10, 1, 0xFF1FD35F);
      int y = sy;

      for (GlassScreen.Tab t : GlassScreen.Tab.values()) {
         boolean sel = t == this.tab;
         float h = this.hover("tab" + t, in(mx, my, sx, y, sw, itemH));
         if (!sel && h > 0.0F) {
            Glass.roundRect(g, sx, y, sw, itemH, 6, Glass.alpha(352321535, h));
         }

         int color = sel ? -1 : Glass.lerpColor(-6184523, -657926, h);
         Glass.icon(g, t.icon, sx + 9, y + (itemH - t.icon.length) / 2, sel ? 0xFF1FD35F : Glass.lerpColor(-10592654, -6184523, h));
         Fx.text(g, this.font, t.label, sx + 24 + Math.round(h * 2.0F), y + 7, color, false);
         this.hit(sx, y, sw, itemH, () -> this.switchTab(t));
         y += itemH + 3;
      }

      int uy = this.py + this.ph - 62;
      if (s.status == DiscordState.Status.CONNECTED) {
         Glass.card(g, sx, uy, sw, 24, 6, 0.0F);
         Glass.avatar(g, this.font, sx + 5, uy + 5, 14, s.userId, s.userName, false);
         Glass.dot(g, sx + 18, uy + 18, 3, -16382455);
         Glass.dot(g, sx + 18, uy + 18, 2, -13904522);
         Fx.text(g, this.font, Glass.ellipsize(this.font, s.userName, sw - 30), sx + 24, uy + 4, -657926, false);
         String keys = GlassConfig.get().menuKey.describe();
         Fx.text(g, this.font, Glass.ellipsize(this.font, keys, sw - 30), sx + 24, uy + 14, -10592654, false);
      } else {
         String keys = "Menü: " + GlassConfig.get().menuKey.describe();
         Fx.text(g, this.font, Glass.ellipsize(this.font, keys, sw), sx + (sw - Math.min(sw, Fx.width(this.font, keys))) / 2, uy + 10, -10592654, false);
      }

      boolean running = DiscordClient.INSTANCE.isRunning();
      int by = this.py + this.ph - 32;
      this.bigButton(g, mx, my, "conn", sx, by, sw, 22, running ? "Trennen" : "Verbinden", Glass.ICON_PLUG, !running, running ? -45730 : 0xFF2EFF7A, () -> {
         if (DiscordClient.INSTANCE.isRunning()) {
            DiscordClient.INSTANCE.disconnect();
         } else {
            DiscordClient.INSTANCE.connect();
         }
      });
   }

   private void heading(GuiGraphicsExtractor g, String title, String sub) {
      Glass.bigText(g, this.font, title, this.cx(), this.cy() - 1, 1.25F, -1);
      if (sub != null) {
         Fx.text(g, this.font, Glass.ellipsize(this.font, sub, this.cw()), this.cx(), this.cy() + 12, -10592654, false);
      }
   }

   private void renderOverview(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      int cx = this.cx();
      int cy = this.cy();
      int cw = this.cw();
      int ch = this.ch();
      DiscordClient dc = DiscordClient.INSTANCE;
      if (s.status != DiscordState.Status.CONNECTED) {
         int h = 150;
         int y = cy + (ch - h) / 2 - 6;
         Glass.card(g, cx, y, cw, h, 10, 0.0F);
         int ix = cx + cw / 2 - 16;
         int iy = y + 14;
         float p = s.status != DiscordState.Status.AUTHORIZING && s.status != DiscordState.Status.CONNECTING ? 0.5F : Glass.pulse(900L);
         Glass.glow(g, ix, iy, 32, 32, 16, s.status.color, 6, 0.5F + 0.5F * p);
         Glass.roundGradient(g, ix, iy, 32, 32, 16, Glass.withAlpha(s.status.color, 80), Glass.withAlpha(s.status.color, 24));
         Glass.roundOutline(g, ix, iy, 32, 32, 16, Glass.withAlpha(s.status.color, 160));
         Glass.icon(g, Glass.ICON_PLUG, ix + 12, iy + 11, -1);

         String title = switch (s.status) {
            case AUTHORIZING -> "Bestätige in Discord";
            case CONNECTING -> "Verbinde mit Discord…";
            case ERROR -> "Verbindung fehlgeschlagen";
            case WAITING -> "Warte auf Discord…";
            default -> "Nicht verbunden";
         };
         int tw = Math.round(Fx.width(this.font, title) * 1.25F);
         Glass.bigText(g, this.font, title, cx + (cw - tw) / 2, iy + 40, 1.25F, -1);
         String detail = !s.statusDetail.isEmpty()
            ? s.statusDetail
            : (
               s.status == DiscordState.Status.AUTHORIZING
                  ? "In Discord ist ein Fenster aufgegangen – klicke dort auf „Autorisieren“."
                  : "Öffne die Discord-App auf diesem PC – die Mod verbindet sich automatisch. Keine Einrichtung nötig."
            );
         List<FormattedCharSequence> lines = this.font.split(FormattedText.of(detail), cw - 40);
         int ly = iy + 56;

         for (FormattedCharSequence line : lines) {
            Fx.text(g, this.font, line, cx + (cw - Fx.width(this.font, line)) / 2, ly, -6184523, false);
            ly += 10;
         }

         if (!dc.isRunning()) {
            int bw = 96;
            int gap = 6;
            int bx = cx + (cw - bw * 2 - gap) / 2;
            int by = y + h - 30;
            this.bigButton(g, mx, my, "ov-conn", bx, by, bw, 20, "Verbinden", Glass.ICON_PLUG, true, 0xFF2EFF7A, dc::connect);
            this.bigButton(
               g, mx, my, "ov-set", bx + bw + gap, by, bw, 20, "Optionen", Glass.ICON_GEAR, false, 0xFF2EFF7A, () -> this.switchTab(GlassScreen.Tab.SETTINGS)
            );
         }
      } else {
         int heroH = 46;
         Glass.roundGradientH(g, cx, cy, cw, heroH, 10, Glass.withAlpha(0xFF2EFF7A, 64), Glass.withAlpha(0xFF1FD35F, 16));
         Glass.roundOutline(g, cx, cy, cw, heroH, 10, Glass.withAlpha(0xFF2EFF7A, 85));
         int iconBox = 28;
         int ibx = cx + 9;
         int iby = cy + 9;
         Glass.roundRect(g, ibx, iby, iconBox, iconBox, 8, 1073741824);
         Glass.roundOutline(g, ibx, iby, iconBox, iconBox, 8, 822083583);
         if (s.inCall()) {
            Glass.icon(g, Glass.ICON_SPEAKER, ibx + 10, iby + 10, -13904522);
            String name = s.channelName == null ? "" : s.channelName;
            Glass.bigText(g, this.font, Glass.ellipsize(this.font, name, (int)((cw - 120) / 1.25F)), ibx + iconBox + 8, iby + 1, 1.25F, -1);
            int sy2 = iby + 16;
            int tx = ibx + iconBox + 8;
            String guild = s.guildName == null ? "" : s.guildName;
            Fx.text(g, this.font, Glass.ellipsize(this.font, guild, cw - 140), tx, sy2, -6184523, false);
            int gx = tx + Math.min(Fx.width(this.font, guild), cw - 140) + 6;
            Glass.signal(g, gx, sy2, s.ping);
            if (s.ping >= 0.0F) {
               Fx.text(g, this.font, Math.round(s.ping) + " ms", gx + 11, sy2, -10592654, false);
            }

            String conn = !s.voiceConnection.isEmpty() && !s.voiceConnection.equals("VOICE_CONNECTED")
               ? s.voiceConnection.replace("VOICE_", "").replace("_", " ").toLowerCase()
               : "Live";
            int pc = conn.equals("Live") ? -13904522 : -16059;
            Glass.pill(g, this.font, cx + cw - 9, cy + 9, (conn.equals("Live") ? "● " : "") + conn, pc);
         } else {
            Glass.icon(g, Glass.ICON_SPEAKER, ibx + 10, iby + 10, -10592654);
            Glass.bigText(g, this.font, "Kein Sprachkanal", ibx + iconBox + 8, iby + 1, 1.25F, -1);
            Fx.text(g, this.font, "Tritt einem Kanal bei, um ihn hier zu sehen.", ibx + iconBox + 8, iby + 16, -6184523, false);
         }

         int by = cy + heroH + 8;
         int bw = (cw - 12) / 3;
         this.bigButton(g, mx, my, "mute", cx, by, bw, 24, s.selfMute ? "Stumm" : "Mikrofon", Glass.ICON_MIC, s.selfMute, -45730, dc::toggleMute);
         if (s.selfMute) {
            Glass.slash(g, cx + 10, by + 6, 11, -1);
         }

         this.bigButton(g, mx, my, "deaf", cx + bw + 6, by, bw, 24, s.selfDeaf ? "Taub" : "Ton", Glass.ICON_HEADPHONES, s.selfDeaf, -45730, dc::toggleDeafen);
         if (s.selfDeaf) {
            Glass.slash(g, cx + bw + 16, by + 6, 11, -1);
         }

         if (s.inCall()) {
            this.bigButton(g, mx, my, "leave", cx + 2 * (bw + 6), by, bw, 24, "Auflegen", Glass.ICON_HANGUP, true, -45730, dc::leaveVoice);
         } else {
            this.bigButton(
               g,
               mx,
               my,
               "pick",
               cx + 2 * (bw + 6),
               by,
               bw,
               24,
               "Kanal wählen",
               Glass.ICON_SERVER,
               true,
               0xFF2EFF7A,
               () -> this.switchTab(GlassScreen.Tab.SERVERS)
            );
         }

         int ly = by + 32;
         int lh = cy + ch - ly;
         Glass.card(g, cx, ly, cw, lh, 10, 0.0F);
         if (!s.inCall()) {
            List<FormattedCharSequence> lines = this.font
               .split(
                  FormattedText.of(
                     "Sobald du in einem Call bist, siehst du hier alle Teilnehmer – wer gerade spricht, wer stumm oder taub geschaltet ist. Über „Kanal wählen“ kannst du direkt beitreten."
                  ),
                  cw - 24
               );
            int ty = ly + 12;

            for (FormattedCharSequence line : lines) {
               Fx.text(g, this.font, line, cx + 12, ty, -6184523, false);
               ty += 10;
            }
         } else {
            List<DiscordState.Member> members = s.members();
            long speaking = members.stream().filter(mxx -> mxx.speaking).count();
            Fx.text(g, this.font, "IM CALL", cx + 10, ly + 7, -10592654, false);
            Fx.text(g, this.font, String.valueOf(members.size()), cx + 10 + Fx.width(this.font, "IM CALL") + 4, ly + 7, 0xFF1FD35F, false);
            if (speaking > 0L) {
               Glass.pill(g, this.font, cx + cw - 8, ly + 5, speaking + " spricht", -13904522);
            }

            int rowH = 18;
            int top = ly + 20;
            int visible = Math.max(1, (lh - 24) / rowH);
            this.memberScroll = Math.max(0, Math.min(this.memberScroll, members.size() - visible));
            this.scrolls.add(new GlassScreen.Scroll(cx, ly, cw, lh, d -> this.memberScroll = this.memberScroll + d));
            g.enableScissor(cx + 1, top - 1, cx + cw - 1, ly + lh - 3);
            int ry = top;

            for (int i = this.memberScroll; i < members.size() && i < this.memberScroll + visible; i++) {
               DiscordState.Member m = members.get(i);
               float h = this.hover("m" + m.id, in(mx, my, cx + 5, ry, cw - 10, rowH - 2));
               float sp = this.anim("sp" + m.id, m.speaking, 10.0F);
               Glass.roundRect(g, cx + 5, ry, cw - 10, rowH - 2, 6, Glass.lerpColor(Glass.alpha(285212671, h), Glass.withAlpha(-13904522, 34), sp));
               if (sp > 0.01F) {
                  Glass.roundOutline(g, cx + 5, ry, cw - 10, rowH - 2, 6, Glass.withAlpha(-13904522, (int)(96.0F * sp)));
               }

               Glass.avatar(g, this.font, cx + 10, ry + 1, 14, m.id, m.name, m.speaking);
               int rx = cx + cw - 14;
               if (m.deafened()) {
                  rx = Glass.iconBadge(g, rx, ry + 3, Glass.ICON_HEADPHONES, -45730);
               }

               if (m.muted()) {
                  rx = Glass.iconBadge(g, rx, ry + 3, Glass.ICON_MIC, -45730);
               }

               if (m.serverMute || m.serverDeaf) {
                  rx = Glass.pill(g, this.font, rx, ry + 4, "Server", -45730);
               }

               boolean me = m.id.equals(s.userId);
               String name = m.name;
               int nameColor = m.speaking ? -1 : (!m.muted() && !m.deafened() ? -657926 : -10592654);
               String shown = Glass.ellipsize(this.font, name, rx - (cx + 30) - (me ? 24 : 0));
               Fx.text(g, this.font, shown, cx + 30, ry + 4, nameColor, false);
               if (me) {
                  Fx.text(g, this.font, "(du)", cx + 30 + Fx.width(this.font, shown) + 4, ry + 4, -10592654, false);
               }

               ry += rowH;
            }

            g.disableScissor();
            this.scrollbar(g, cx + cw - 4, top, lh - 24, members.size(), visible, this.memberScroll);
         }
      }
   }

   private void renderServers(GuiGraphicsExtractor g, int mx, int my, DiscordState s) {
      this.heading(g, "Server", "Suche einen Server und klicke auf einen Sprachkanal.");
      int cx = this.cx();
      int cw = this.cw();
      if (!DiscordClient.INSTANCE.isConnected()) {
         if (this.guildBox != null) {
            this.guildBox.visible = false;
         }

         if (this.channelBox != null) {
            this.channelBox.visible = false;
         }

         this.emptyState(g, cx, this.cy() + 30, cw, this.ch() - 30, "Nicht mit Discord verbunden.");
      } else {
         int rw = Fx.width(this.font, "Aktualisieren") + 14;
         if (this.guildBox != null) {
            this.guildBox.visible = true;
         }

         this.smallButton(g, mx, my, "refresh", cx + cw - rw, this.cy() - 1, rw, 14, "Aktualisieren", () -> {
            DiscordClient.INSTANCE.refreshGuilds();
            if (this.selectedGuild != null) {
               DiscordClient.INSTANCE.loadChannels(this.selectedGuild);
            }
         });
         int top = this.cy() + 26;
         int h = this.ch() - 26;
         int lw = (cw - 6) / 2;
         Glass.card(g, cx, top, lw, h, 10, 0.0F);
         Glass.card(g, cx + lw + 6, top, cw - lw - 6, h, 10, 0.0F);
         List<DiscordState.Guild> allGuilds = new ArrayList<>(s.guilds);
         List<DiscordState.Guild> guilds = filter(allGuilds, this.guildFilter, DiscordState.Guild::name);
         int rowH = 18;
         int listTop = top + 22;
         int visible = (h - 26) / rowH;
         if (this.guildBox != null) {
            this.searchBar(g, mx, my, this.guildBox, cx + 4, top + 4, lw - 8, "g");
         }

         this.guildScroll = Math.max(0, Math.min(this.guildScroll, guilds.size() - visible));
         this.scrolls.add(new GlassScreen.Scroll(cx, top, lw, h, d -> this.guildScroll = this.guildScroll + d));
         if (allGuilds.isEmpty()) {
            Fx.text(g, this.font, "Lade Server…", cx + 10, listTop + 6, -10592654, false);
         } else if (guilds.isEmpty()) {
            String none = "Kein Server für „" + this.guildFilter.trim() + "“";
            Fx.text(g, this.font, Glass.ellipsize(this.font, none, lw - 16), cx + 10, listTop + 6, -10592654, false);
         } else if (!this.guildFilter.isBlank()) {
            String cnt = guilds.size() + " von " + allGuilds.size();
            Fx.text(g, this.font, cnt, cx + lw - Fx.width(this.font, cnt) - 8, top + h - 11, -10592654, false);
         }

         g.enableScissor(cx + 1, listTop - 1, cx + lw - 1, top + h - 12);
         int ry = listTop;

         for (int i = this.guildScroll; i < guilds.size() && i < this.guildScroll + visible + 1; i++) {
            DiscordState.Guild gu = guilds.get(i);
            boolean sel = gu.id().equals(this.selectedGuild);
            float hv = this.hover("g" + gu.id(), in(mx, my, cx + 4, ry, lw - 8, rowH - 2));
            if (sel) {
               Glass.roundGradientH(g, cx + 4, ry, lw - 8, rowH - 2, 6, Glass.withAlpha(0xFF2EFF7A, 112), Glass.withAlpha(0xFF2EFF7A, 24));
            } else if (hv > 0.0F) {
               Glass.roundRect(g, cx + 4, ry, lw - 8, rowH - 2, 6, Glass.alpha(352321535, hv));
            }

            Glass.avatar(g, this.font, cx + 8, ry + 2, 12, gu.id(), gu.name(), false);
            Fx.text(g, this.font, Glass.ellipsize(this.font, gu.name(), lw - 34), cx + 25, ry + 4, sel ? -1 : Glass.lerpColor(-6184523, -657926, hv), false);
            this.hit(cx + 4, ry, lw - 8, rowH - 2, () -> {
               this.selectedGuild = gu.id();
               this.channelScroll = 0;
               DiscordClient.INSTANCE.loadChannels(gu.id());
            });
            ry += rowH;
         }

         g.disableScissor();
         this.scrollbar(g, cx + lw - 4, listTop, h - 26, guilds.size(), visible, this.guildScroll);
         int kx = cx + lw + 6;
         int kw = cw - lw - 6;
         if (this.channelBox != null) {
            this.channelBox.visible = this.selectedGuild != null;
         }

         if (this.selectedGuild == null) {
            this.emptyState(g, kx, top, kw, h, "← Wähle einen Server");
         } else {
            this.searchBar(g, mx, my, this.channelBox, kx + 4, top + 4, kw - 8, "c");
            List<DiscordState.Channel> allChannels = s.channelsByGuild.get(this.selectedGuild);
            if (allChannels != null && !allChannels.isEmpty()) {
               List<DiscordState.Channel> channels = filter(allChannels, this.channelFilter, DiscordState.Channel::name);
               if (channels.isEmpty()) {
                  String none = "Kein Kanal für „" + this.channelFilter.trim() + "“";
                  Fx.text(g, this.font, Glass.ellipsize(this.font, none, kw - 16), kx + 10, listTop + 6, -10592654, false);
               } else {
                  this.channelScroll = Math.max(0, Math.min(this.channelScroll, channels.size() - visible));
                  this.scrolls.add(new GlassScreen.Scroll(kx, top, kw, h, d -> this.channelScroll = this.channelScroll + d));
                  g.enableScissor(kx + 1, listTop - 1, kx + kw - 1, top + h - 3);
                  ry = listTop;

                  for (int i = this.channelScroll; i < channels.size() && i < this.channelScroll + visible + 1; i++) {
                     DiscordState.Channel c = channels.get(i);
                     boolean current = c.id().equals(s.channelId);
                     float hv = this.hover("c" + c.id(), in(mx, my, kx + 4, ry, kw - 8, rowH - 2));
                     if (current) {
                        Glass.roundRect(g, kx + 4, ry, kw - 8, rowH - 2, 6, Glass.withAlpha(-13904522, 48));
                        Glass.roundOutline(g, kx + 4, ry, kw - 8, rowH - 2, 6, Glass.withAlpha(-13904522, 112));
                     } else if (hv > 0.0F) {
                        Glass.roundRect(g, kx + 4, ry, kw - 8, rowH - 2, 6, Glass.alpha(352321535, hv));
                     }

                     Glass.icon(g, Glass.ICON_SPEAKER, kx + 9, ry + 4, current ? -13904522 : Glass.lerpColor(-10592654, -6184523, hv));
                     int right = kx + kw - 10;
                     if (current) {
                        right = Glass.pill(g, this.font, right, ry + 3, "Live", -13904522);
                     } else if (hv > 0.05F) {
                        right = Glass.pill(g, this.font, right, ry + 3, "Beitreten", 0xFF2EFF7A);
                     }

                     g.text(
                        this.font,
                        Glass.ellipsize(this.font, c.name(), right - (kx + 22) - 2),
                        kx + 22,
                        ry + 4,
                        current ? -1 : Glass.lerpColor(-6184523, -657926, hv),
                        false
                     );
                     if (!current) {
                        this.hit(kx + 4, ry, kw - 8, rowH - 2, () -> DiscordClient.INSTANCE.joinVoice(c.id()));
                     }

                     ry += rowH;
                  }

                  g.disableScissor();
                  this.scrollbar(g, kx + kw - 4, listTop, h - 26, channels.size(), visible, this.channelScroll);
               }
            } else {
               Fx.text(g, this.font, allChannels == null ? "Lade Kanäle…" : "Keine Sprachkanäle", kx + 10, listTop + 6, -10592654, false);
            }
         }
      }
   }

   private void renderHotkeys(GuiGraphicsExtractor g, int mx, int my) {
      this.heading(g, "Hotkeys", "Linksklick: belegen  •  Rechtsklick: entfernen");
      GlassConfig cfg = GlassConfig.get();
      int cx = this.cx();
      int cw = this.cw();
      Object[][] rows = new Object[][]{
         {"Menü öffnen", "Dieses Menü ein-/ausblenden", cfg.menuKey, Glass.ICON_WAVE},
         {"Stummschalten", "Mikrofon an/aus", cfg.muteKey, Glass.ICON_MIC},
         {"Taubschalten", "Ton & Mikrofon an/aus", cfg.deafenKey, Glass.ICON_HEADPHONES},
         {"HUD ein/aus", "Overlay ingame zeigen", cfg.hudKey, Glass.ICON_SERVER},
         {"Call verlassen", "Aktuellen Sprachkanal verlassen", cfg.leaveKey, Glass.ICON_HANGUP}
      };
      int y = this.cy() + 26;
      int rowH = 32;

      for (Object[] row : rows) {
         Hotkey hk = (Hotkey)row[2];
         boolean cap = this.capturing == hk;
         float hv = this.hover("hk" + row[0], in(mx, my, cx, y, cw, rowH - 4));
         Glass.card(g, cx, y, cw, rowH - 4, 8, hv * 0.6F);
         Glass.roundRect(g, cx + 6, y + 5, 18, 18, 6, Glass.withAlpha(0xFF2EFF7A, 48));
         String[] icon = (String[])row[3];
         Glass.icon(g, icon, cx + 6 + (18 - Glass.iconW(icon)) / 2, y + 5 + (18 - icon.length) / 2, 0xFF1FD35F);
         Fx.text(g, this.font, (String)row[0], cx + 30, y + 5, -657926, false);
         Fx.text(g, this.font, (String)row[1], cx + 30, y + 15, -10592654, false);
         String label = cap ? "Drücke Tasten…" : hk.describe();
         int kw = Math.max(70, Fx.width(this.font, label) + 16);
         int kx = cx + cw - kw - 6;
         int ky = y + 5;
         float kh = this.hover("key" + row[0], in(mx, my, kx, ky, kw, 18));
         if (cap) {
            float p = Glass.pulse(700L);
            Glass.glow(g, kx, ky, kw, 18, 5, 0xFF2EFF7A, 4, 0.5F + 0.5F * p);
            Glass.roundRect(g, kx, ky, kw, 18, 5, Glass.withAlpha(0xFF2EFF7A, 96));
            Glass.roundOutline(g, kx, ky, kw, 18, 5, 0xFF2EFF7A);
         } else {
            Glass.roundRect(g, kx, ky + 2, kw, 16, 5, -16777216);
            Glass.roundGradient(g, kx, ky, kw, 16, 5, Glass.lerpColor(-14408658, -13619140, kh), Glass.lerpColor(-15263970, -14671830, kh));
            Glass.roundOutline(g, kx, ky, kw, 16, 5, Glass.lerpColor(822083583, Glass.withAlpha(0xFF2EFF7A, 192), kh));
         }

         int tc = !hk.isBound() && !cap ? -10592654 : -1;
         Fx.text(g, this.font, label, kx + (kw - Fx.width(this.font, label)) / 2, ky + (cap ? 5 : 4), tc, false);
         this.hits.add(new GlassScreen.Hit(kx, ky, kw, 18, () -> this.capturing = cap ? null : hk, () -> {
            if (hk != cfg.menuKey) {
               hk.key = -1;
               hk.ctrl = hk.shift = hk.alt = false;
               GlassConfig.save();
            }

            this.capturing = null;
         }));
         y += rowH;
      }
   }

   private void renderSettings(GuiGraphicsExtractor g, int mx, int my) {
      GlassConfig cfg = GlassConfig.get();
      int cx = this.cx();
      int cw = this.cw();
      int y = this.cy();
      this.section(g, "VERBINDUNG", cx, y);
      this.toggleRow(g, mx, my, cx, y + 11, cw, "Eigene Discord-App verwenden (Profi-Modus)", cfg.useOwnApp, () -> {
         cfg.useOwnApp = !cfg.useOwnApp;
         GlassConfig.save();
         this.rebuildWidgets();
         DiscordClient.INSTANCE.disconnect();
         DiscordClient.INSTANCE.connect();
      });
      if (!cfg.useOwnApp) {
         int iy = y + 31;
         Glass.roundGradientH(g, cx, iy, cw, 36, 7, Glass.withAlpha(-13904522, 48), Glass.withAlpha(-13904522, 8));
         Glass.roundOutline(g, cx, iy, cw, 36, 7, Glass.withAlpha(-13904522, 96));
         Glass.roundRect(g, cx + 7, iy + 9, 18, 18, 6, Glass.withAlpha(-13904522, 80));
         Glass.icon(g, Glass.ICON_CHECK, cx + 13, iy + 15, -1);
         Fx.text(g, this.font, "Ohne Einrichtung – verbindet sich automatisch", cx + 32, iy + 8, -657926, false);
         Fx.text(g, this.font, Glass.ellipsize(this.font, "Einmal in Discord auf „Autorisieren“ klicken, fertig.", cw - 40), cx + 32, iy + 20, -6184523, false);
      }

      String[] labels = new String[]{"App-ID", "Secret"};
      EditBox[] boxes = new EditBox[]{this.idBox, this.secretBox};

      for (int i = 0; i < 2 && cfg.useOwnApp; i++) {
         EditBox b = boxes[i];
         int fy = y + 31 + i * 20;
         Fx.text(g, this.font, labels[i], cx + 2, fy + 4, -6184523, false);
         if (b != null) {
            int bx = b.getX() - 6;
            int bw = b.getWidth() + 12;
            float f = this.anim("f" + i, b.isFocused(), 12.0F);
            if (f > 0.01F) {
               Glass.glow(g, bx, fy, bw, 16, 5, 0xFF2EFF7A, 3, f);
            }

            Glass.roundRect(g, bx, fy, bw, 16, 5, Glass.lerpColor(1610612736, -1879048192, f));
            Glass.roundOutline(g, bx, fy, bw, 16, 5, Glass.lerpColor(620756991, 0xFF2EFF7A, f));
         }
      }

      y += 76;
      this.section(g, "OPTIONEN", cx, y);
      y += 11;
      int colW = (cw - 6) / 2;
      this.toggleRow(g, mx, my, cx, y, colW, "Auto-Verbinden", cfg.autoConnect, () -> cfg.autoConnect = !cfg.autoConnect);
      this.toggleRow(g, mx, my, cx + colW + 6, y, colW, "Rich Presence", cfg.richPresence, () -> cfg.richPresence = !cfg.richPresence);
      y += 20;
      this.toggleRow(g, mx, my, cx, y, colW, "HUD anzeigen", cfg.hudEnabled, () -> cfg.hudEnabled = !cfg.hudEnabled);
      this.toggleRow(g, mx, my, cx + colW + 6, y, colW, "Nur Sprechende", cfg.hudOnlySpeaking, () -> cfg.hudOnlySpeaking = !cfg.hudOnlySpeaking);
      y += 20;
      this.toggleRow(g, mx, my, cx, y, colW, "Benachrichtigungen", cfg.notifications, () -> cfg.notifications = !cfg.notifications);
      String[] corners = new String[]{"Oben links", "Oben rechts", "Unten links", "Unten rechts"};
      int px2 = cx + colW + 6;
      float hv = this.hover("corner", in(mx, my, px2, y, colW, 17));
      Glass.card(g, px2, y, colW, 17, 6, hv);
      Fx.text(g, this.font, "HUD", px2 + 7, y + 5, -6184523, false);
      String c = corners[Math.floorMod(cfg.hudCorner, 4)] + " ›";
      Fx.text(g, this.font, c, px2 + colW - Fx.width(this.font, c) - 7, y + 5, 0xFF1FD35F, false);
      this.hit(px2, y, colW, 17, () -> {
         cfg.hudCorner = (cfg.hudCorner + 1) % 4;
         GlassConfig.save();
      });
      y += 20;
      Glass.card(g, cx, y, cw, 17, 6, 0.0F);
      Fx.text(g, this.font, "HUD-Deckkraft", cx + 7, y + 5, -6184523, false);
      String pct = cfg.hudOpacity + "%";
      Fx.text(g, this.font, pct, cx + cw - Fx.width(this.font, pct) - 7, y + 5, -657926, false);
      int sx = cx + 90;
      int sw = cw - 90 - 36;
      int sy = y + 7;
      Glass.roundRect(g, sx, sy, sw, 3, 1, 822083583);
      float frac = (cfg.hudOpacity - 20) / 80.0F;
      Glass.roundGradientH(g, sx, sy, Math.max(2, Math.round(sw * frac)), 3, 1, 0xFF2EFF7A, 0xFF1FD35F);
      int kx = sx + Math.round(sw * frac);
      Glass.glow(g, kx - 4, sy - 3, 9, 9, 4, 0xFF2EFF7A, 2, 1.0F);
      Glass.roundRect(g, kx - 4, sy - 3, 9, 9, 4, -1);
      this.slider = new GlassScreen.Hit(sx - 4, y, sw + 8, 17, () -> {
         double rel = (this.lastMouseX - sx) / sw;
         cfg.hudOpacity = (int)Math.round(Math.max(0.0, Math.min(1.0, rel)) * 8.0) * 10 + 20;
         GlassConfig.save();
      }, null);
      this.hits.add(this.slider);
      y += 24;
      this.bigButton(g, mx, my, "forget", cx, y, (cw - 6) / 2, 20, "Login zurücksetzen", Glass.ICON_CLOSE, false, -45730, DiscordClient.INSTANCE::forgetLogin);
      this.bigButton(g, mx, my, "reconnect", cx + (cw - 6) / 2 + 6, y, cw - (cw - 6) / 2 - 6, 20, "Neu verbinden", Glass.ICON_PLUG, true, 0xFF2EFF7A, () -> {
         DiscordClient.INSTANCE.disconnect();
         DiscordClient.INSTANCE.connect();
      });
      y += 26;
      String help = cfg.useOwnApp
         ? "Eigene App: discord.com/developers → App erstellen → OAuth2-Redirect „http://localhost“ → ID + Secret eintragen."
         : "Die Anmeldung läuft über Discords StreamKit-Overlay. Rich Presence („Spielt Minecraft“) gibt es nur mit eigener App.";

      for (FormattedCharSequence line : this.font.split(FormattedText.of(help), cw)) {
         if (y > this.py + this.ph - 14) {
            break;
         }

         Fx.text(g, this.font, line, cx, y, -10592654, false);
         y += 10;
      }
   }

   private void section(GuiGraphicsExtractor g, String text, int x, int y) {
      Fx.text(g, this.font, text, x, y, -10592654, false);
      int lx = x + Fx.width(this.font, text) + 6;
      Glass.roundGradientH(g, lx, y + 4, this.cx() + this.cw() - lx, 1, 0, 822083583, 16777215);
   }

   private void emptyState(GuiGraphicsExtractor g, int x, int y, int w, int h, String text) {
      Fx.text(g, this.font, text, x + (w - Fx.width(this.font, text)) / 2, y + h / 2 - 4, -10592654, false);
   }

   private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int total, int visible, int offset) {
      if (total > visible) {
         int th = Math.max(12, h * visible / total);
         int ty = y + (h - th) * offset / Math.max(1, total - visible);
         Glass.roundRect(g, x, y, 2, h, 1, 352321535);
         Glass.roundRect(g, x, ty, 2, th, 1, Glass.withAlpha(0xFF2EFF7A, 192));
      }
   }

   private void toggleRow(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean value, Runnable flip) {
      float hv = this.hover("t" + label, in(mx, my, x, y, w, 17));
      float on = this.anim("tg" + label, value, 14.0F);
      Glass.card(g, x, y, w, 17, 6, hv);
      Fx.text(g, this.font, Glass.ellipsize(this.font, label, w - 36), x + 7, y + 5, Glass.lerpColor(-6184523, -657926, Math.max(hv, on)), false);
      int tx = x + w - 28;
      int ty = y + 3;
      int track = Glass.lerpColor(822083583, 0xFF2EFF7A, on);
      if (on > 0.05F) {
         Glass.glow(g, tx, ty, 22, 11, 5, 0xFF2EFF7A, 3, on);
      }

      Glass.roundRect(g, tx, ty, 22, 11, 5, track);
      int kx = tx + 1 + Math.round(on * 11.0F);
      Glass.roundRect(g, kx, ty + 1, 9, 9, 4, -1);
      this.hit(x, y, w, 17, () -> {
         flip.run();
         GlassConfig.save();
      });
   }

   private void bigButton(
      GuiGraphicsExtractor g, int mx, int my, String id, int x, int y, int w, int h, String label, String[] icon, boolean active, int color, Runnable action
   ) {
      float hv = this.hover("b" + id, in(mx, my, x, y, w, h));
      float ac = this.anim("a" + id, active, 12.0F);
      if (ac > 0.01F) {
         Glass.glow(g, x, y, w, h, 7, color, 4, ac * (0.6F + 0.4F * hv));
         Glass.roundGradient(
            g, x, y, w, h, 7, Glass.alpha(Glass.lerpColor(color, -1, 0.12F + 0.1F * hv), ac), Glass.alpha(Glass.lerpColor(color, -16777216, 0.25F), ac)
         );
      }

      if (ac < 0.99F) {
         Glass.roundRect(g, x, y, w, h, 7, Glass.alpha(Glass.lerpColor(318767103, 620756991, hv), 1.0F - ac));
      }

      Glass.roundOutline(g, x, y, w, h, 7, Glass.lerpColor(Glass.lerpColor(587202559, 1358954495, hv), 1358954495, ac));
      g.fill(x + 7, y + 1, x + w - 7, y + 2, 553648127);
      int iw = icon == null ? 0 : Glass.iconW(icon) + 5;
      String text = Glass.ellipsize(this.font, label, w - 10 - iw);
      int total = iw + Fx.width(this.font, text);
      int sx = x + (w - total) / 2;
      if (icon != null) {
         Glass.icon(g, icon, sx, y + (h - icon.length) / 2, -1);
      }

      Fx.text(g, this.font, text, sx + iw, y + (h - 8) / 2 + 1, -1, false);
      this.hit(x, y, w, h, action);
   }

   private void smallButton(GuiGraphicsExtractor g, int mx, int my, String id, int x, int y, int w, int h, String label, Runnable action) {
      float hv = this.hover("s" + id, in(mx, my, x, y, w, h));
      Glass.roundRect(g, x, y, w, h, 5, Glass.lerpColor(318767103, Glass.withAlpha(0xFF2EFF7A, 128), hv));
      Glass.roundOutline(g, x, y, w, h, 5, Glass.lerpColor(620756991, 0xFF2EFF7A, hv));
      Fx.text(g, this.font, label, x + (w - Fx.width(this.font, label)) / 2, y + (h - 8) / 2 + 1, -657926, false);
      this.hit(x, y, w, h, action);
   }

   private void hit(int x, int y, int w, int h, Runnable action) {
      this.hits.add(new GlassScreen.Hit(x, y, w, h, action, null));
   }

   private static boolean in(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
      this.lastMouseX = e.x();
      if (super.mouseClicked(e, doubleClick)) {
         return true;
      } else {
         for (EditBox b : new EditBox[]{this.idBox, this.secretBox, this.redirectBox, this.guildBox, this.channelBox}) {
            if (b != null) {
               b.setFocused(false);
            }
         }

         this.setFocused(null);
         boolean right = e.button() == 3;
         boolean left = e.button() == 1;

         for (int i = this.hits.size() - 1; i >= 0; i--) {
            GlassScreen.Hit h = this.hits.get(i);
            if (h.contains(e.x(), e.y())) {
               Runnable r = right ? h.right() : (left ? h.left() : null);
               if (r != null) {
                  r.run();
                  return true;
               }
            }
         }

         this.capturing = null;
         return false;
      }
   }

   public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
      if (this.tab == GlassScreen.Tab.SETTINGS && e.button() == 1) {
         this.lastMouseX = e.x();
         GlassScreen.Hit sl = this.slider;
         if (sl != null && sl.contains(e.x(), e.y())) {
            sl.left().run();
            return true;
         }
      }

      return super.mouseDragged(e, dx, dy);
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      for (GlassScreen.Scroll a : this.scrolls) {
         if (mx >= a.x() && my >= a.y() && mx < a.x() + a.w() && my < a.y() + a.h()) {
            a.by().accept(sy > 0.0 ? -1 : (sy < 0.0 ? 1 : 0));
            return true;
         }
      }

      return super.mouseScrolled(mx, my, sx, sy);
   }

   public boolean keyPressed(KeyEvent e) {
      if (this.capturing != null) {
         if (e.isEscape()) {
            this.capturing = null;
            return true;
         } else if (Hotkey.isModifierKey(e.key())) {
            return true;
         } else {
            this.capturing.key = e.key();
            this.capturing.ctrl = e.hasControlDown();
            this.capturing.shift = e.hasShiftDown();
            this.capturing.alt = e.hasAltDown();
            this.capturing = null;
            GlassConfig.save();
            return true;
         }
      } else {
         boolean typing = this.getFocused() instanceof EditBox;
         if (!typing && GlassConfig.get().menuKey.matches(e.key(), e.hasControlDown(), e.hasShiftDown(), e.hasAltDown())) {
            this.onClose();
            return true;
         } else {
            return super.keyPressed(e);
         }
      }
   }

   private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
      boolean contains(double mx, double my) {
         return mx >= this.x && my >= this.y && mx < this.x + this.w && my < this.y + this.h;
      }
   }

   private record Scroll(int x, int y, int w, int h, Consumer<Integer> by) {
   }

   private static enum Tab {
      OVERVIEW("Übersicht", Glass.ICON_WAVE),
      SERVERS("Server", Glass.ICON_SERVER),
      HOTKEYS("Hotkeys", Glass.ICON_KEYBOARD),
      SETTINGS("Einstellungen", Glass.ICON_GEAR);

      final String label;
      final String[] icon;

      private Tab(String label, String[] icon) {
         this.label = label;
         this.icon = icon;
      }
   }
}
