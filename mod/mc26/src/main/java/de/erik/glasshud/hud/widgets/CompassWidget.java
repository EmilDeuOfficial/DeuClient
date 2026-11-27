package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Fx;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompassWidget extends Widget {
   private static final String[] DIRS = new String[]{"N", "NO", "O", "SO", "S", "SW", "W", "NW"};

   CompassWidget() {
      super("compass", "Kompass", Glass.ICON_COMPASS, new Widget.Option("deg", "Gradzahl", new String[]{"Aus", "An"}, 1));
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(true, 1, 0, 0, 6);
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      return size(156, c.opt("deg", 1) == 1 ? 29 : 21);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      Glass.card(g, 0, 0, w, h, 7, c.op);
      float b = CoordsWidget.bearing(c.d.yaw);
      int left = 9;
      int right = w - 9;
      float half = (right - left) / 2.0F;
      float cx = w / 2.0F;
      int base = 16;

      // Gleichmaessige Bewegung: Der Abstand zweier Marken (5 Grad) ist eine ganze Zahl Bildschirm-Pixel.
      // Alle Marken und Buchstaben verschieben sich dann in 1-Pixel-Schritten exakt gleich (kein Ruckeln durch Runden).
      double k = DeuRender.totalScale(g);
      int step = Math.max(1, (int)Math.round(5.0 * (half / 90.0) * k));
      double ppd = step / (5.0 * k); // GUI-Pixel pro Grad
      double range = half / ppd; // sichtbarer Bereich in Grad (nach links/rechts)
      int a0 = (int)Math.floor((b - range) / 5.0) * 5;
      int pw = (int)Math.max(2L, Math.round(1.25 * k)); // Strichbreite in Bildschirm-Pixeln (2 bei GUI 1, 3 bei GUI 2, 4 bei GUI 3)

      for (int a = a0; a <= b + range; a += 5) {
         double delta = a - b;
         double t = Math.abs(delta) / range;
         float fade = (float)(1.0 - t * t);
         if (fade <= 0.02F) {
            continue;
         }

         int ang = (a % 360 + 360) % 360;
         long fx = Math.round(cx * k + delta * ppd * k); // Mitte der Marke in Bildschirm-Pixeln
         boolean cardinal = ang % 90 == 0;
         boolean inter = ang % 45 == 0;
         boolean major = ang % 15 == 0;
         int th = inter ? 4 : (major ? 3 : 2);
         int tc = cardinal ? Glass.alpha(-855048, fade) : Glass.withAlpha(16777215, Math.round((inter ? 150 : (major ? 105 : 65)) * fade));
         // abgerundeter Strich (Kapsel), pixelgenau
         long tickLeft = fx - pw / 2;
         DeuRender.pill(g, (float)(tickLeft / k), base - th, pw, (int)Math.round(th * k), tc);
         if (inter) {
            String s = DIRS[ang / 45];
            int col = ang == 0 ? -38022 : (cardinal ? -855048 : -5855048);
            float centerGui = (float)((tickLeft + pw / 2.0) / k);
            Fx.textAt(g, s, centerGui - Fx.widthF(s) / 2.0F, 1.5F, Glass.alpha(col, fade));
         }
      }

      // Mitte: abgerundete Marke in Akzentfarbe
      int mw = pw + 2;
      long mLeft = Math.round(cx * k) - mw / 2;
      DeuRender.pill(g, (float)(mLeft / k), base - 6, mw, (int)Math.round(7 * k), Glass.accent());
      Glass.glow(g, Math.round(cx) - 1, base - 6, 2, 7, 1, Glass.accent(), 2, 0.8F);
      if (c.opt("deg", 1) == 1) {
         String s = Math.round(b) + "°  " + CoordsWidget.dirName(c.d.yaw);
         c.textC(s, cx, 19.0F, -5855048);
      }
   }
}
