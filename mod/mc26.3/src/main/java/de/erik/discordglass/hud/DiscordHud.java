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
         float op = cfg.hudOpacity / 100.0F;
         int sw = g.guiWidth();
         int sh = g.guiHeight();
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
            int x = right ? sw - 158 - 6 : 6;
            int y = bottom ? sh - total - 6 : 6;
            if (showCard) {
               darkPanel(g, x, y, 158, cardH, op);
               Glass.roundGradient(g, x + 3, y + 6, 2, 18, 1, 0xFF1FD35F, 0xFF2EFF7A);
               boolean live = s.voiceConnection.isEmpty() || "VOICE_CONNECTED".equals(s.voiceConnection);
               Glass.icon(g, Glass.ICON_SPEAKER, x + 9, y + 6, live ? -13904522 : -16059);
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
                  Glass.signal(g, x + 9 + gw + 5, y + 17, s.ping);
                  Fx.text(g, font, Math.round(s.ping) + "ms", x + 9 + gw + 16, y + 17, -10592654, false);
               }

               if (rows > 0) {
                  Glass.roundGradientH(g, x + 7, y + 28, 144, 1, 0, 822083583, 16777215);
               }

               int ry = y + 32;

               for (int i = 0; i < rows; i++) {
                  DiscordState.Member m = members.get(i);
                  if (m.speaking) {
                     Glass.roundRect(g, x + 4, ry - 1, 150, 13, 4, Glass.alpha(Glass.withAlpha(-13904522, 38), op * 1.3F));
                  }

                  Glass.avatar(g, font, x + 8, ry + 1, 10, m.id, m.name, m.speaking);
                  int rx = x + 158 - 8;
                  if (m.deafened()) {
                     rx = Glass.iconBadge(g, rx, ry + 1, Glass.ICON_HEADPHONES, -45730);
                  }

                  if (m.muted()) {
                     rx = Glass.iconBadge(g, rx, ry + 1, Glass.ICON_MIC, -45730);
                  }

                  int nameColor = m.speaking ? -1 : (!m.deafened() && !m.muted() ? -6184523 : -10592654);
                  Fx.text(g, font, Glass.ellipsize(font, m.name, rx - (x + 23) - 2), x + 23, ry + 2, nameColor, m.speaking);
                  ry += 14;
               }

               if (members.size() > maxRows) {
                  Fx.text(g, font, "+" + (members.size() - maxRows) + " weitere", x + 23, ry + 1, -10592654, false);
               }

               y += cardH + 4;
            } else if (showPill) {
               darkPanel(g, x, y, 158, cardH, op);
               Glass.icon(g, s.selfDeaf ? Glass.ICON_HEADPHONES : Glass.ICON_MIC, x + 8, y + 4, -45730);
               Glass.slash(g, x + 7, y + 3, 11, -45730);
               Fx.text(g, font, s.selfDeaf ? "Discord: Taub geschaltet" : "Discord: Stumm geschaltet", x + 22, y + 5, -657926, true);
               y += cardH + 4;
            }

            for (DiscordState.Notification n : toasts) {
               float age = (float)(now - n.time()) / 6000.0F;
               float fade = age > 0.85F ? (1.0F - age) / 0.15F : Math.min(1.0F, age * 12.0F);
               int slide = Math.round((1.0F - Math.min(1.0F, age * 10.0F)) * 12.0F) * (right ? 1 : -1);
               int tx = x + slide;
               darkPanel(g, tx, y, 158, 26, op * fade);
               Glass.roundGradient(g, tx + 3, y + 5, 2, 16, 1, Glass.alpha(0xFF1FD35F, fade), Glass.alpha(0xFF2EFF7A, fade));
               Fx.text(g, font, Glass.ellipsize(font, n.title(), 142), tx + 9, y + 4, Glass.alpha(-657926, Math.max(0.05F, fade)), false);
               Fx.text(g, font, Glass.ellipsize(font, n.body(), 142), tx + 9, y + 14, Glass.alpha(-6184523, Math.max(0.05F, fade)), false);
               int bw = Math.round(140.0F * (1.0F - age));
               g.fill(tx + 9, y + 23, tx + 9 + bw, y + 24, Glass.alpha(Glass.withAlpha(0xFF2EFF7A, 144), fade));
               y += 30;
            }
         }
      }
   }

   private static void darkPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, float op) {
      Glass.roundRect(g, x, y, w, h, 7, Glass.alpha(-536541944, op * 1.1F));
      Glass.roundGradient(g, x, y, w, h, 7, Glass.alpha(385875967, op), Glass.alpha(50331647, op));
      Glass.roundOutline(g, x, y, w, h, 7, Glass.alpha(721420287, op));
   }

   private static String nz(String s) {
      return s == null ? "" : s;
   }
}
