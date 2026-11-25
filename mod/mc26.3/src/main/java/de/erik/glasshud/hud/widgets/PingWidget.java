package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PingWidget extends Widget {
   PingWidget() {
      super("ping", "Ping", Glass.ICON_SIGNAL);
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(true, 0, 0, 6, 95);
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      return size(22 + c.tw("Ping") + 6 + c.tw("000 ms") + 8, 21);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      Glass.card(g, 0, 0, w, h, 7, c.op);
      int ping = c.d.ping;
      int col = quality(ping, 60.0F, 130.0F, false);
      int active = ping < 50 ? 4 : (ping < 100 ? 3 : (ping < 180 ? 2 : 1));

      // Balken von links (kurz) nach rechts (lang): hud_ping4 ist der kuerzeste, hud_ping1 der laengste
      for (int i = 0; i < 4; i++) {
         int c2 = i < active ? col : Glass.withAlpha(16777215, 40);
         Glass.svgIcon(g, "hud_ping" + (4 - i), 6, 4, Glass.WICON, c2);
      }

      c.text("Ping", 22, 6, -5855048);
      String v = ping + " ms";
      c.textR(v, w - 8, 6, col);
   }
}
