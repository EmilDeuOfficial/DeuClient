package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CoordsWidget extends Widget {
   private static final String[] DIRS = new String[]{"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
   private static final int[] AXIS = new int[]{-30056, -7929930, -7945473};

   CoordsWidget() {
      super(
         "coords",
         "Koordinaten",
         Glass.ICON_PIN,
         new Widget.Option("decimals", "Nachkommastellen", new String[]{"Keine", "Eine"}, 0),
         new Widget.Option("dir", "Blickrichtung", new String[]{"Aus", "An"}, 1)
      );
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(true, 0, 0, 6, 6);
   }

   private static String num(double v, int decimals) {
      return decimals > 0 ? String.format("%.1f", v) : String.valueOf((long)Math.floor(v));
   }

   static String dirName(float yaw) {
      float bearing = ((yaw + 180.0F) % 360.0F + 360.0F) % 360.0F;
      return DIRS[Math.round(bearing / 45.0F) % 8];
   }

   static float bearing(float yaw) {
      return ((yaw + 180.0F) % 360.0F + 360.0F) % 360.0F;
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      int dec = c.opt("decimals", 0);
      int vw = Math.max(c.tw(num(c.d.x, dec)), Math.max(c.tw(num(c.d.y, dec)), c.tw(num(c.d.z, dec))));
      int w = Math.max(76, 22 + vw + 8);
      boolean dir = c.opt("dir", 1) == 1;
      if (dir) {
         w = Math.max(w, 16 + c.tw("NO  000°") + 22);
      }

      int h = 43 + (dir ? 15 : 0) + 4;
      return size(w, h);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      int dec = c.opt("decimals", 0);
      Glass.card(g, 0, 0, w, h, 7, c.op);
      Glass.roundGradient(g, 4, 6, 2, h - 12, 1, Glass.accent(), Glass.accentDim());
      String[] vals = new String[]{num(c.d.x, dec), num(c.d.y, dec), num(c.d.z, dec)};
      String[] labels = new String[]{"X", "Y", "Z"};
      int y = 7;

      for (int i = 0; i < 3; i++) {
         c.text(labels[i], 11, y, AXIS[i]);
         c.textR(vals[i], w - 8, y, -855048);
         y += 11;
      }

      if (c.opt("dir", 1) == 1) {
         Glass.roundGradientH(g, 10, ++y, w - 18, 1, 0, 16777215, 822083583);
         y += 4;
         c.text(dirName(c.d.yaw), 11, y, Glass.accent());
         c.textR(Math.round(bearing(c.d.yaw)) + "°", w - 8, y, -5855048);
      }
   }
}
