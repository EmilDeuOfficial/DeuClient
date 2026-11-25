package de.erik.glasshud;

import de.erik.glasshud.config.Hotkey;
import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.EditorScreen;
import de.erik.glasshud.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

public class GlassHudClient implements ClientModInitializer {
   public static final String MOD_ID = "glasshud";
   private boolean wasDown;

   public void onInitializeClient() {
      HudConfig.load();
      // vor dem Boss-Balken einhaengen: Chat, Tab-Liste, Scoreboard und F3 liegen dadurch ueber dem HUD
      HudElementRegistry.attachElementBefore(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("glasshud", "main"), (g, dt) -> {
         Minecraft mc = Minecraft.getInstance();
         Screen current = mc.gui.screen();
         if (current == null || current instanceof net.minecraft.client.gui.screens.ChatScreen) {
            HudRenderer.renderAll(g, mc, false);
         }
      });
      HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original -> (g, dt) -> {
         HudConfig cfg = HudConfig.get();
         if (!cfg.hideVanillaEffects || !cfg.w("potions").enabled) {
            original.extractRenderState(g, dt);
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(this::tick);
   }

   private void tick(Minecraft mc) {
      if (DemoMode.ENABLED) {
         DemoMode.tick(mc);
      }

      Hotkey key = HudConfig.get().editorKey;
      boolean down = key.isPressedNow();
      Screen screen = mc.gui.screen();
      boolean allowed = screen == null || screen instanceof TitleScreen || screen instanceof PauseScreen;
      if (down && !this.wasDown && allowed) {
         mc.gui.setScreen(new EditorScreen(screen));
      }

      this.wasDown = down;
   }
}
