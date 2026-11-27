package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FpsWidget extends Widget {
   private static final int GRAPH_W = 30;

   FpsWidget() {
      super("fps", "FPS", Glass.ICON_GAUGE, new Widget.Option("graph", "Diagramm", new String[]{"Aus", "An"}, 1));
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(true, 0, 0, 6, 70);
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      boolean graph = c.opt("graph", 1) == 1;
      int w = 22 + c.tw("FPS") + 6 + c.tw("000") + (graph ? 38 : 0) + 8;
      return size(w, 21);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      Glass.card(g, 0, 0, w, h, 7, c.op);
      int col = quality(c.d.fps, 60.0F, 30.0F, true);
      Glass.svgIcon(g, "hud_fps", 6, 4, Glass.WICON, col);
      c.text("FPS", 22, 6, -5855048);
      int vx = 22 + c.tw("FPS") + 6;
      c.text(String.valueOf(c.d.fps), vx, 6, col);
      if (c.opt("graph", 1) == 1) {
         this.graph(c, w - 8 - GRAPH_W, 4, GRAPH_W, 13);
      }
   }

   /** Catmull-Rom-Kurve zwischen p1 und p2 (t = 0..1) */
   private static float spline(float p0, float p1, float p2, float p3, float t) {
      return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t * t * t);
   }

   /**
    * Glatter Verlauf statt Balken: weiche Kurve ueber alle Messwerte, in Bildschirm-Pixeln gezeichnet,
    * mit duenner heller Linie, Farbverlauf darunter und fliessender Bewegung nach links.
    */
   private void graph(Widget.Ctx c, int gx, int gy, int gwGui, int ghGui) {
      GuiGraphicsExtractor g = c.g;
      double k = DeuRender.totalScale(g);
      int gw = (int)Math.round(gwGui * k);
      int gh = (int)Math.round(ghGui * k);
      float[] hist = c.d.fpsHist;
      int n = hist.length;
      float step = gw / 26.0F; // Pixel pro Messwert (alle 250 ms ein neuer Wert)
      int m = Math.min(n, (int)Math.ceil(gw / step) + 3);
      float frac = Math.max(0.0F, Math.min(1.0F, (System.currentTimeMillis() - c.d.lastFpsSample) / 250.0F));
      float max = 60.0F;
      float[] vals = new float[m];

      for (int i = 0; i < m; i++) {
         vals[i] = hist[n - m + i];
         max = Math.max(max, vals[i]);
      }

      max *= 1.08F;
      float x0 = gw - frac * step - (m - 1) * step; // x des aeltesten Messwerts
      float top = 1.5F;
      float bottom = gh - 1.5F;
      int accent = Glass.accent();

      g.pose().pushMatrix();
      g.pose().translate(gx, gy);
      g.pose().scale((float)(1.0 / k), (float)(1.0 / k));
      float prev = Float.NaN;
      for (int x = 0; x < gw; x++) {
         float pos = (x + 0.5F - x0) / step;
         int j = (int)Math.floor(pos);
         float v;
         if (j >= m - 1) {
            v = vals[m - 1];
         } else if (j < 0) {
            v = vals[0];
         } else {
            v = spline(vals[Math.max(j - 1, 0)], vals[j], vals[j + 1], vals[Math.min(j + 2, m - 1)], pos - j);
         }

         float yf = bottom - Math.max(0.0F, Math.min(1.0F, v / max)) * (bottom - top);
         float ymin = Float.isNaN(prev) ? yf : Math.min(prev, yf);
         float ymax = Float.isNaN(prev) ? yf : Math.max(prev, yf);
         prev = yf;
         float age = x / (float)(gw - 1);
         float fade = 0.25F + 0.75F * age;
         int y0 = (int)Math.floor(ymin);
         int yEnd = Math.min(gh, (int)Math.floor(ymax) + 2); // Linie ca. 2 Bildschirm-Pixel dick
         int line = Glass.alpha(Glass.withAlpha(accent, 255), fade);
         float cover = 1.0F - (ymin - y0); // oberste Pixelreihe nur anteilig: weiche Kante
         g.fill(x, y0, x + 1, y0 + 1, Glass.alpha(line, cover));
         if (yEnd > y0 + 1) {
            g.fill(x, y0 + 1, x + 1, yEnd, line);
         }

         if (yEnd < gh) {
            g.fillGradient(x, yEnd, x + 1, gh, Glass.withAlpha(accent, Math.round(95.0F * fade)), Glass.withAlpha(accent, 0));
         }
      }

      g.pose().popMatrix();
   }
}
