package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import java.time.LocalTime;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ClockWidget extends Widget {
   ClockWidget() {
      super(
         "clock",
         "Uhrzeit",
         Glass.ICON_CLOCK,
         new Widget.Option("ingame", "Spielzeit", new String[]{"Aus", "An"}, 1),
         new Widget.Option("sec", "Sekunden", new String[]{"Aus", "An"}, 0)
      );
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(false, 2, 2, -6, -6);
   }

   private static String real(Widget.Ctx c) {
      LocalTime t = LocalTime.now();
      return c.opt("sec", 0) == 1
         ? String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond())
         : String.format("%02d:%02d", t.getHour(), t.getMinute());
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      int tw = c.tw(c.opt("sec", 0) == 1 ? "00:00:00" : "00:00");
      int w = 24 + tw + 9;
      // zwei Zeilen zu je 20 px: Mitte der ersten bei 10,5, der zweiten bei 30,5 (oben und unten gleich viel Luft)
      return size(w, c.opt("ingame", 1) == 1 ? 41 : 21);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      Glass.card(g, 0, 0, w, h, 7, c.op);
      // Icons direkt links der Uhrzeit, beide Zeilen mit gleicher Textspalte (x = 24)
      Glass.svgIcon(g, "hud_time", 6, 4, Glass.WICON, Glass.GOOD);
      c.text(real(c), 24, 6, -855048);
      if (c.opt("ingame", 1) == 1) {
         Glass.roundGradientH(g, 8, 20, w - 16, 1, 0, 16777215, 754974719);
         long t = (c.d.clockTicks % 24000L + 24000L) % 24000L;
         long hour = (t / 1000L + 6L) % 24L;
         long min = t % 1000L * 60L / 1000L;
         boolean day = t < 12000L;
         // zweite Zeile: Sonne (Tag) oder Mond (Nacht)
         Glass.svgIcon(g, day ? "hud_sun" : "hud_moon", 6, 24, Glass.WICON, day ? -11930 : -2564875);
         c.text(String.format("%02d:%02d", hour, min), 24, 26, day ? -8054 : -4669973);
      }
   }
}
