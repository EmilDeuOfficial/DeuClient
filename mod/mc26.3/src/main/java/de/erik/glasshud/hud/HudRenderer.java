package de.erik.glasshud.hud;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import de.erik.glasshud.hud.widgets.Widget;
import de.erik.glasshud.hud.widgets.Widgets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudRenderer {
   private static final Map<String, HudRenderer.Rect> BOUNDS = new LinkedHashMap<>();
   public static boolean forceSample;

   private HudRenderer() {
   }

   public static HudRenderer.Rect bounds(String id) {
      return BOUNDS.get(id);
   }

   public static int[] resolve(HudConfig.W wc, int sw, int sh, int screenW, int screenH) {
      int x = wc.ax * (screenW - sw) / 2 + wc.offX;
      int y = wc.ay * (screenH - sh) / 2 + wc.offY;
      x = Math.max(0, Math.min(Math.max(0, screenW - sw), x));
      y = Math.max(0, Math.min(Math.max(0, screenH - sh), y));
      return new int[]{x, y};
   }

   public static void renderAll(GuiGraphicsExtractor g, Minecraft mc, boolean editor) {
      HudConfig cfg = HudConfig.get();
      HudData live = HudData.LIVE;
      if (!forceSample) {
         live.update(mc);
      }

      HudData d = !forceSample && (!editor || live.inWorld) ? live : HudData.sample();
      BOUNDS.clear();
      if (editor || d.inWorld) {
         int screenW = g.guiWidth();
         int screenH = g.guiHeight();
         float op = Glass.opacity();

         for (Widget wd : Widgets.ALL) {
            HudConfig.W wc = cfg.w(wd.id);
            if (wc.enabled) {
               Widget.Ctx c = new Widget.Ctx(g, mc.font, d, op, editor, wc);
               int[] sz = wd.measure(c);
               if (sz[0] > 0 && sz[1] > 0) {
                  float s = Math.max(0.5F, Math.min(3.0F, wc.scale));
                  int ws = Math.round(sz[0] * s);
                  int hs = Math.round(sz[1] * s);
                  int[] pos = resolve(wc, ws, hs, screenW, screenH);
                  BOUNDS.put(wd.id, new HudRenderer.Rect(pos[0], pos[1], ws, hs));
                  g.pose().pushMatrix();
                  g.pose().translate(pos[0], pos[1]);
                  g.pose().scale(s, s);
                  wd.draw(c, sz[0], sz[1]);
                  g.pose().popMatrix();
               }
            }
         }
      }
   }

   public record Rect(int x, int y, int w, int h) {
      public boolean contains(double px, double py) {
         return px >= this.x && py >= this.y && px < this.x + this.w && py < this.y + this.h;
      }
   }
}
