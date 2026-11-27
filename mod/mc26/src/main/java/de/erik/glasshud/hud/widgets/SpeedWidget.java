package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SpeedWidget extends Widget {
   SpeedWidget() {
      super("speed", "Tempo", Glass.ICON_SPEED, new Widget.Option("unit", "Einheit", new String[]{"Blöcke/s", "km/h"}, 0));
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(false, 0, 0, 6, 120);
   }

   private float shown;
   private long shownAt;

   private String value(Widget.Ctx c) {
      // Anzeige nur alle 0,4 s aktualisieren, damit die Zahl lesbar bleibt
      long now = System.currentTimeMillis();
      if (now - this.shownAt > 400L) {
         this.shown = c.d.speed;
         this.shownAt = now;
      }
      float v = this.shown;
      return c.opt("unit", 0) == 1 ? String.format("%.0f km/h", v * 3.6F) : String.format("%.1f b/s", v);
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      return size(22 + Math.max(c.tw("00.0 b/s"), c.tw("000 km/h")) + 8, 21);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      Glass.card(g, 0, 0, w, h, 7, c.op);
      Glass.svgIcon(g, "hud_speed", 6, 4, Glass.WICON, Glass.GOOD);
      c.textR(this.value(c), w - 8, 6, -855048);
   }
}
