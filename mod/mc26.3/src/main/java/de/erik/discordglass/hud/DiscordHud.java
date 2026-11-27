package de.erik.discordglass.hud;

import eu.deusmp.deucore.Fx;
import de.erik.discordglass.config.GlassConfig;
import de.erik.discordglass.discord.DiscordClient;
import de.erik.discordglass.discord.DiscordState;
import de.erik.discordglass.gui.Glass;
import de.erik.discordglass.gui.GlassScreen;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DiscordHud {
   private static final int WIDTH = 158;
   private static final int MARGIN = 6;
   private static final int ROW = 14;
   private static final long TOAST_MS = 6000L;

   private DiscordHud() {
   }

   public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
      GlassConfig cfg = GlassConfig.get();
      Minecraft mc = Minecraft.getInstance();
      net.minecraft.client.gui.screens.Screen current = mc.gui.screen();
      if (cfg.hudEnabled && eu.deusmp.deucore.DeuConfig.get().discordEnabled && (current == null || current instanceof net.minecraft.client.gui.screens.ChatScreen)) {
         Font font = mc.font;
         DiscordState s = DiscordClient.INSTANCE.state;
         float zoom = de.erik.glasshud.hud.HudRenderer.snapScale(cfg.scale);
         int sw = Math.round(g.guiWidth() / zoom);
         int sh = Math.round(g.guiHeight() / zoom);
         boolean right = cfg.hudCorner == 1 || cfg.hudCorner == 3;
         boolean bottom = cfg.hudCorner == 2 || cfg.hudCorner == 3;
         List<DiscordState.Member> members = s.members();
         if (cfg.hudOnlySpeaking) {
            members.removeIf(m -> !m.speaking);
         }

         boolean showCard = s.inCall();
         boolean showPill = !showCard && s.status == DiscordState.Status.CONNECTED && (s.selfMute || s.selfDeaf);
         int maxRows = 10;
         int rows = Math.min(members.size(), maxRows);
         int cardH = 0;
         if (showCard) {
            cardH = 30 + rows * 14 + (members.size() > maxRows ? 11 : 0) + (rows > 0 ? 4 : 0);
         }

         if (showPill) {
            cardH = 18;
         }

         long now = System.currentTimeMillis();
         s.notifications.removeIf(nx -> now - nx.time() > 6000L);
         List<DiscordState.Notification> toasts = s.notifications;
         int toastH = toasts.size() * 30;
         int total = cardH + (cardH > 0 && toastH > 0 ? 4 : 0) + toastH;
         if (total != 0) {
            g.pose().pushMatrix();
            g.pose().scale(zoom, zoom);
            int x = right ? sw - 158 - 6 : 6;
            int y = clearOfHud(x, bottom ? sh - total - 6 : 6, total, bottom, zoom);
            if (showCard) {
               darkPanel(g, x, y, 158, cardH, 1.0F);
               boolean live = s.voiceConnection.isEmpty() || "VOICE_CONNECTED".equals(s.voiceConnection);
               Glass.hudIcon(g, "mod_volume", x + 9, y + 6, live ? -13904522 : -16059);
               int rightEdge = x + 158 - 7;
               if (s.selfDeaf) {
                  rightEdge = Glass.iconBadge(g, rightEdge, y + 6, Glass.ICON_HEADPHONES, -45730);
               }

               if (s.selfMute) {
                  rightEdge = Glass.iconBadge(g, rightEdge, y + 6, Glass.ICON_MIC, -45730);
               }

               Fx.text(g, font, Glass.ellipsize(font, nz(s.channelName), rightEdge - (x + 20) - 2), x + 20, y + 6, -657926, true);
               String guild = nz(s.guildName);
               int gw = Math.min(Fx.width(font, guild), 98);
               Fx.text(g, font, Glass.ellipsize(font, guild, 98), x + 9, y + 17, -10592654, false);
               if (s.ping >= 0.0F) {
                  de.erik.glasshud.hud.widgets.PingWidget.bars(g, x + 9 + gw + 5, y + 17, Glass.HUD_ICON, Math.round(s.ping));
                  Fx.text(g, font, Math.round(s.ping) + "ms", x + 9 + gw + 16, y + 17, -10592654, false);
               }

               if (rows > 0) {
                  g.fill(x + 7, y + 28, x + 151, y + 29, 0x22FFFFFF);
               }

               int ry = y + 32;

               for (int i = 0; i < rows; i++) {
                  DiscordState.Member m = members.get(i);
                  if (m.speaking) {
                     Glass.roundRect(g, x + 4, ry - 1, 150, 13, 4, Glass.alpha(Glass.withAlpha(-13904522, 38), 1.0F));
                  }

                  Glass.avatar(g, font, x + 8, ry + 1, 10, m.id, m.name, m.speaking, m.avatar);
                  int rx = x + 158 - 8;
                  if (m.deafened()) {
                     rx = Glass.iconBadge(g, rx, ry + 2, Glass.ICON_HEADPHONES, -45730);
                  }

                  if (m.muted()) {
                     rx = Glass.iconBadge(g, rx, ry + 2, Glass.ICON_MIC, -45730);
                  }

                  int nameColor = m.speaking ? -1 : (!m.deafened() && !m.muted() ? -6184523 : -10592654);
                  Fx.text(g, font, Glass.ellipsize(font, m.name, rx - (x + 23) - 2), x + 23, ry + 2, nameColor, m.speaking);
                  ry += 14;
               }

               if (members.size() > maxRows) {
                  Fx.text(g, font, "+" + (members.size() - maxRows) + " " + eu.deusmp.deucore.Tr.t("weitere"), x + 23, ry + 1, -10592654, false);
               }

               y += cardH + 4;
            } else if (showPill) {
               darkPanel(g, x, y, 158, cardH, 1.0F);
               Glass.offIcon(g, s.selfDeaf ? Glass.ICON_HEADPHONES : Glass.ICON_MIC, x + 8, y + 5, -45730);
               Fx.text(g, font, s.selfDeaf ? "Discord: Taub geschaltet" : "Discord: Stumm geschaltet", x + 20, y + 5, -657926, true);
               y += cardH + 4;
            }

            for (DiscordState.Notification n : toasts) {
               float age = (float)(now - n.time()) / 6000.0F;
               float fade = age > 0.85F ? (1.0F - age) / 0.15F : Math.min(1.0F, age * 12.0F);
               int slide = Math.round((1.0F - Math.min(1.0F, age * 10.0F)) * 12.0F) * (right ? 1 : -1);
               int tx = x + slide;
               darkPanel(g, tx, y, 158, 26, fade);
               Fx.text(g, font, Glass.ellipsize(font, n.title(), 142), tx + 9, y + 4, Glass.alpha(-657926, Math.max(0.05F, fade)), false);
               Fx.text(g, font, Glass.ellipsize(font, n.body(), 142), tx + 9, y + 14, Glass.alpha(-6184523, Math.max(0.05F, fade)), false);
               int bw = Math.round(140.0F * (1.0F - age));
               g.fill(tx + 9, y + 23, tx + 9 + bw, y + 24, Glass.alpha(Glass.withAlpha(0xFF2EFF7A, 144), fade));
               y += 30;
            }

            g.pose().popMatrix();
         }
      }
   }

   /**
    * Weicht HUD-Anzeigen in derselben Ecke aus (z. B. den Effekten oben rechts): oben nach unten, unten nach oben.
    * x, y und die Hoehe gelten in der vergroesserten Zeichenebene des Overlays.
    */
   private static int clearOfHud(int x, int y, int height, boolean bottom, float zoom) {
      boolean moved = true;
      for (int pass = 0; moved && pass < 8; pass++) {
         moved = false;
         for (de.erik.glasshud.hud.HudRenderer.Rect r : de.erik.glasshud.hud.HudRenderer.allBounds()) {
            boolean overlaps = x * zoom < r.x() + r.w() && (x + WIDTH) * zoom > r.x()
                  && y * zoom < r.y() + r.h() && (y + height) * zoom > r.y();
            if (overlaps) {
               y = bottom ? (int)Math.floor((r.y() - 4) / zoom) - height : (int)Math.ceil((r.y() + r.h() + 4) / zoom);
               moved = true;
            }
         }
      }

      return Math.max(0, y);
   }

   /** Hintergrund wie bei den HUD-Elementen: aus, dunkel oder schwarz, mit Transparenz */
   private static void darkPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, float fade) {
      GlassConfig cfg = GlassConfig.get();
      if (cfg.bgStyle != 0) {
         float op = fade * (1.0F - Math.max(0, Math.min(100, cfg.bgTransparency)) / 100.0F);
         Glass.roundRect(g, x, y, w, h, 7, Glass.alpha(cfg.bgStyle == 2 ? 0xE6000000 : 0xE6121418, op));
      }
   }

   private static String nz(String s) {
      return s == null ? "" : s;
   }
}
