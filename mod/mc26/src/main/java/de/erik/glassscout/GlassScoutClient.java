package de.erik.glassscout;

import de.erik.glassscout.config.ScoutConfig;
import de.erik.glassscout.gui.Overlay;
import de.erik.glassscout.scan.InvScan;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.Command;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterExtract;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class GlassScoutClient implements ClientModInitializer {
   public static final String MOD_ID = "glassscout";
   private static final long WINDOW_MS = 8000L;
   private static final Pattern TITLE = Pattern.compile("(?i).*(invsee|inventory|inventar|inv\\b|ausr[üu]stung|equipment).*");
   private static volatile long lastCommandMs;
   private static volatile String lastTarget = "";
   private static final Set<Screen> REGISTERED = Collections.newSetFromMap(new WeakHashMap<>());
   private static final Map<Screen, String> TARGETS = new WeakHashMap<>();

   public void onInitializeClient() {
      ScoutConfig.load();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         if (DemoMode.ENABLED) {
            DemoMode.tick(mc);
         }
      });
      ClientSendMessageEvents.COMMAND.register((Command)command -> noteCommand(command));
      ScreenEvents.AFTER_INIT.register((AfterInit)(client, screen, w, h) -> {
         if (screen instanceof AbstractContainerScreen<?> acs) {
            if (REGISTERED.add(screen)) {
               String target = decideTarget(acs);
               if (target != null) {
                  TARGETS.put(screen, target);
                  ScreenEvents.afterExtract(screen).register((AfterExtract)(s, g, mx, my, dt) -> {
                     Minecraft mc = Minecraft.getInstance();
                     InvScan.Result r = InvScan.analyze(acs, mc);
                     Overlay.draw(g, mc, s, r, TARGETS.getOrDefault(s, ""));
                  });
               }
            }
         }
      });
   }

   static void noteCommand(String command) {
      String[] t = command.trim().split("\\s+");
      if (t.length != 0) {
         String name = t[0].toLowerCase();
         int colon = name.indexOf(58);
         if (colon >= 0) {
            name = name.substring(colon + 1);
         }

         if (ScoutConfig.get().commands.contains(name)) {
            lastCommandMs = System.currentTimeMillis();
            lastTarget = t.length > 1 ? t[1] : "";
         }
      }
   }

   private static String decideTarget(AbstractContainerScreen<?> screen) {
      ScoutConfig cfg = ScoutConfig.get();
      boolean afterCommand = System.currentTimeMillis() - lastCommandMs < 8000L;
      String title = screen.getTitle().getString();
      if (afterCommand) {
         return !lastTarget.isEmpty() ? lastTarget : title;
      } else if (cfg.mode == 1) {
         return title;
      } else {
         return TITLE.matcher(title).matches() ? title : null;
      }
   }

   static boolean hasOverlay(Screen screen) {
      return eu.deusmp.deucore.DeuConfig.get().scoutEnabled && TARGETS.containsKey(screen);
   }

   static void forgetInvsee() {
      lastCommandMs = 0L;
   }

   static void simulateInvsee(String target) {
      lastCommandMs = System.currentTimeMillis();
      lastTarget = target;
   }
}
