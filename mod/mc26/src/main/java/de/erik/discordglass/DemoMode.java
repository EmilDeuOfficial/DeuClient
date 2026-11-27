package de.erik.discordglass;

import de.erik.discordglass.config.GlassConfig;
import de.erik.discordglass.discord.DiscordClient;
import de.erik.discordglass.discord.DiscordState;
import de.erik.discordglass.gui.GlassScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class DemoMode {
   static final boolean ENABLED = Boolean.getBoolean("discordglass.demo");
   private static int ticks = -1;

   private DemoMode() {
   }

   static void tick(Minecraft mc) {
      if (ENABLED && mc.level != null && mc.player != null) {
         ticks++;
         DiscordState s = DiscordClient.INSTANCE.state;
         switch (ticks) {
            case 1:
               fill(s);
               mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 6000));
               mc.player.setXRot(-12.0F);
               mc.player.setYRot(mc.player.getYRot() + 140.0F);
               break;
            case 140:
               s.pushNotification("Luna  •  #minecraft", "Kommst du gleich in den Call? Wir bauen die Basis!");
               s.pushNotification("Finn", "gg, die Farm läuft endlich \ud83c\udf89".replace(" \ud83c\udf89", "!"));
               break;
            case 160:
               shot(mc, "01_hud");
               break;
            case 170:
               mc.gui.setScreen(GlassScreen.demo(null, 0, null, false));
               break;
            case 215:
               shot(mc, "02_overview");
               break;
            case 220:
               mc.gui.setScreen(GlassScreen.demo(null, 0, "g2", false));
               break;
            case 265:
               shot(mc, "03_channels");
               break;
            case 270:
               mc.gui.setScreen(GlassScreen.demo(null, 2, null, true));
               break;
            case 315:
               shot(mc, "04_capture");
               break;
            case 320:
               mc.gui.setScreen(GlassScreen.demo(null, 2, null, false));
               break;
            case 365:
               shot(mc, "05_settings");
               break;
            case 366:
               s.status = DiscordState.Status.DISCONNECTED;
               mc.gui.setScreen(GlassScreen.demo(null, 0, null, false));
               break;
            case 369:
               shot(mc, "05b_offline");
               s.status = DiscordState.Status.CONNECTED;
               break;
            case 370:
               mc.gui.setScreen(null);
               GlassConfig.get().hudCorner = 1;
               break;
            case 400:
               s.selfMute = true;
               DiscordState.Member me = s.findMember("u1");
               if (me != null) {
                  me.selfMute = true;
               }
               break;
            case 420:
               shot(mc, "06_hud_right");
               break;
            case 440:
               mc.gui.setScreen(GlassScreen.demo(null, 1, null, false));
               break;
            case 560:
               shot(mc, "07_text");
               break;
            case 565:
               mc.gui.setScreen(GlassScreen.demo(null, 0, null, false));
               break;
            case 600:
               shot(mc, "08_call_icons");
               break;
            case 605: // Maus auf dem ersten Freund
               mc.gui.setScreen(GlassScreen.demo(null, 0, null, false).demoHover(70, 92));
               break;
            case 640:
               shot(mc, "09_friends_call");
               break;
            case 645: // Maus auf einem Mitglied im Call: Stern
               mc.gui.setScreen(GlassScreen.demo(null, 0, null, false).demoHover(300, 100));
               break;
            case 680:
               shot(mc, "10_member_star");
               break;
            case 685:
               s.textChannelId = null;
               mc.gui.setScreen(GlassScreen.demo(null, 1, null, false).demoFriend("u5").demoHover(70, 132));
               break;
            case 720:
               shot(mc, "11_friend_without_chat");
               break;
            case 725: // Maus auf einer Nachricht: Stern neben dem Namen
               s.textChannelId = "d1";
               mc.gui.setScreen(GlassScreen.demo(null, 1, null, false).demoHover(300, 74));
               break;
            case 760:
               shot(mc, "12_message_star");
               break;
            case 770:
               mc.stop();
         }

         if (ticks > 1 && ticks % 30 == 0) {
            DiscordState.Member a = s.findMember("u3");
            if (a != null) {
               a.speaking = !a.speaking || ticks < 200;
            }
         }
      }
   }

   private static void shot(Minecraft mc, String name) {
      Screenshot.grab(mc.gameDirectory, "discordglass_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
   }

   private static void fill(DiscordState s) {
      GlassConfig cfg = GlassConfig.get();
      cfg.clientId = "1301234567890123456";
      cfg.clientSecret = "demo-secret-value-xyz";
      cfg.hudEnabled = true;
      cfg.bgTransparency = 20;
      s.status = DiscordState.Status.CONNECTED;
      s.userId = "u1";
      s.userName = "Erik";
      s.channelId = "c1";
      s.channelName = "Survival Talk";
      s.guildId = "g2";
      s.guildName = "Block Builders";
      s.voiceConnection = "VOICE_CONNECTED";
      s.ping = 28.0F;
      Object[][] people = new Object[][]{
         {"u1", "Erik", false, false, false},
         {"u2", "Luna", true, false, false},
         {"u3", "Finn", true, false, false},
         {"u4", "Kiro", false, true, false},
         {"u5", "Mia", false, false, false},
         {"u6", "Noah", false, true, true}
      };

      for (Object[] p : people) {
         DiscordState.Member m = s.member((String)p[0], (String)p[1]);
         m.speaking = (Boolean)p[2];
         m.selfMute = (Boolean)p[3];
         m.selfDeaf = (Boolean)p[4];
      }

      String pic = "https://cdn.discordapp.com/embed/avatars/";
      for (int i = 1; i <= 4; i++) s.guildIcons.put("g" + i, pic + i + ".png");
      DiscordState.Member luna = s.findMember("u2");
      if (luna != null) luna.avatar = pic + "0.png";
      cfg.friends = new java.util.concurrent.CopyOnWriteArrayList<>();
      cfg.friends.add(new GlassConfig.Friend("d1", "u2", "Luna", pic + "0.png"));
      cfg.friends.add(new GlassConfig.Friend("d2", "u3", "Finn", ""));
      cfg.friends.add(new GlassConfig.Friend("", "u5", "Mia", ""));
      s.textChannelId = "d1";
      s.textChannelName = "Luna";
      long t = System.currentTimeMillis() - 600_000L;
      s.messages.clear();
      s.messages.add(new DiscordState.Message("m1", "u2", "Luna", pic + "0.png", "Hey, bist du schon auf dem Server?", t));
      s.messages.add(new DiscordState.Message("m2", "u2", "Luna", pic + "0.png", "Wir bauen gerade die neue Basis am Fluss und brauchen noch Holz. Bring bitte welches mit, wenn du kommst.", t + 20_000L));
      s.messages.add(new DiscordState.Message("m3", "u1", "Erik", "", "Bin in fünf Minuten da!", t + 90_000L));
      s.messages.add(new DiscordState.Message("m4", "u2", "Luna", pic + "0.png", "Super @" + DiscordState.placeholder("jemand") + " [" + DiscordState.placeholder("Anhang") + "]", t + 150_000L));

      s.guilds.clear();
      s.guilds
         .addAll(
            List.of(
               new DiscordState.Guild("g1", "Redstone Lab"),
               new DiscordState.Guild("g2", "Block Builders"),
               new DiscordState.Guild("g3", "PvP Arena"),
               new DiscordState.Guild("g4", "Freundeskreis"),
               new DiscordState.Guild("g5", "Hardcore SMP"),
               new DiscordState.Guild("g6", "Speedrun Community"),
               new DiscordState.Guild("g7", "Pixel Art Club")
            )
         );
      s.channelsByGuild
         .put(
            "g2",
            List.of(
               new DiscordState.Channel("c0", "Lobby", 2),
               new DiscordState.Channel("c1", "Survival Talk", 2),
               new DiscordState.Channel("c2", "Bauprojekt", 2),
               new DiscordState.Channel("c3", "Farm-Planung", 2),
               new DiscordState.Channel("c4", "AFK", 2),
               new DiscordState.Channel("c5", "Stage – Events", 13)
            )
         );
   }
}
