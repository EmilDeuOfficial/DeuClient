package de.erik.discordglass;

import de.erik.discordglass.config.GlassConfig;
import de.erik.discordglass.config.Hotkey;
import de.erik.discordglass.discord.DiscordClient;
import de.erik.discordglass.gui.GlassScreen;
import de.erik.discordglass.hud.DiscordHud;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;

public class DiscordGlassClient implements ClientModInitializer {
   public static final String MOD_ID = "discordglass";
   private final Map<Hotkey, Boolean> wasDown = new IdentityHashMap<>();
   private int activityTimer;

   public void onInitializeClient() {
      GlassConfig.load();
      HudElementRegistry.attachElementBefore(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("discordglass", "hud"), DiscordHud::render);
      ClientTickEvents.END_CLIENT_TICK.register(this::tick);
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> DiscordClient.INSTANCE.disconnect());
   }

   private void tick(Minecraft mc) {
      DiscordClient dc = DiscordClient.INSTANCE;
      if (!eu.deusmp.deucore.DeuConfig.get().discordEnabled) {
         // im Mod-Menue ausgeschaltet: Verbindung trennen, keine Hotkeys, keine Benachrichtigungen
         if (dc.isRunning()) {
            dc.disconnect();
         }

         return;
      }

      if (DemoMode.ENABLED) {
         DemoMode.tick(mc);
      } else {
         dc.tick();
      }

      GlassConfig cfg = GlassConfig.get();
      cfg.adaptDefaultsToLayout();
      Screen screen = mc.gui.screen();
      boolean hotkeysAllowed = screen == null || screen instanceof TitleScreen || screen instanceof PauseScreen;
      if (this.pressed(cfg.menuKey, hotkeysAllowed)) {
         mc.gui.setScreen(new GlassScreen(screen));
      }

      if (this.pressed(cfg.muteKey, hotkeysAllowed)) {
         dc.toggleMute();
      }

      if (this.pressed(cfg.deafenKey, hotkeysAllowed)) {
         dc.toggleDeafen();
      }

      if (this.pressed(cfg.leaveKey, hotkeysAllowed)) {
         dc.leaveVoice();
      }

      if (this.pressed(cfg.hudKey, hotkeysAllowed)) {
         cfg.hudEnabled = !cfg.hudEnabled;
         GlassConfig.save();
      }

      if (++this.activityTimer >= 40) {
         this.activityTimer = 0;
         String details;
         String state;
         if (mc.level == null) {
            details = "Im Hauptmenü";
            state = "Minecraft 26.3";
         } else if (mc.isLocalServer()) {
            details = "Spielt Einzelspieler";
            state = "Minecraft 26.3";
         } else {
            ServerData sd = mc.getCurrentServer();
            details = "Spielt Mehrspieler";
            state = sd == null ? "Minecraft 26.3" : (sd.name != null && !sd.name.isBlank() ? sd.name : "Auf einem Server");
         }

         dc.updateActivity(details, state);
      }
   }

   private boolean pressed(Hotkey hk, boolean allowed) {
      boolean down = hk.isPressedNow();
      Boolean before = this.wasDown.put(hk, down);
      return allowed && down && (before == null || !before);
   }
}
