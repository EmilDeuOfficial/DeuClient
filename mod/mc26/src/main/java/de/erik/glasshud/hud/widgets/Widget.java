package de.erik.glasshud.hud.widgets;

import eu.deusmp.deucore.Fx;
import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.hud.HudData;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Widget {
   public final String id;
   public final String name;
   public final String[] icon;
   public final List<Widget.Option> options;

   protected Widget(String id, String name, String[] icon, Widget.Option... options) {
      this.id = id;
      this.name = name;
      this.icon = icon;
      this.options = List.of(options);
   }

   public abstract HudConfig.W defaults();

   public abstract int[] measure(Widget.Ctx var1);

   public abstract void draw(Widget.Ctx var1, int var2, int var3);

   protected static int[] size(int w, int h) {
      return new int[]{w, h};
   }

   protected static int quality(float v, float good, float warn, boolean higherIsBetter) {
      boolean g = higherIsBetter ? v >= good : v <= good;
      boolean w = higherIsBetter ? v >= warn : v <= warn;
      return g ? -10682467 : (w ? -11930 : -41870);
   }

   public static final class Ctx {
      public final GuiGraphicsExtractor g;
      public final Font font;
      public final HudData d;
      public final float op;
      public final boolean editor;
      public final HudConfig.W cfg;

      public Ctx(GuiGraphicsExtractor g, Font font, HudData d, float op, boolean editor, HudConfig.W cfg) {
         this.g = g;
         this.font = font;
         this.d = d;
         this.op = op;
         this.editor = editor;
         this.cfg = cfg;
      }

      public int opt(String key, int def) {
         Integer v = this.cfg.opts.get(key);
         return v == null ? def : v;
      }

      public int tw(String s) {
         return Fx.width(this.font, s);
      }

      public void text(String s, int x, int y, int color) {
         Fx.text(this.g, this.font, s, x, y, color, false);
      }

      /** Rechtsbuendig mit exakter (Gleitkomma-)Breite, auf Bildschirm-Pixel gerastert */
      public void textR(String s, int rightX, int y, int color) {
         Fx.textAt(this.g, s, rightX - Fx.widthF(s), y, color);
      }

      /** Exakt zentriert um cx (GUI-Pixel, auch Bruchteile), auf Bildschirm-Pixel gerastert */
      public void textC(String s, float cx, float y, int color) {
         Fx.textAt(this.g, s, cx - Fx.widthF(s) / 2.0F, y, color);
      }
   }

   public record Option(String key, String label, String[] values, int def) {
   }
}
