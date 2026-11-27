package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import de.erik.glasshud.hud.HudData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class ArmorWidget extends Widget {
   private static final int ROW = 19;

   ArmorWidget() {
      super(
         "armor",
         "Rüstung",
         Glass.ICON_SHIELD,
         new Widget.Option("text", "Anzeige", new String[]{"Prozent", "Haltbarkeit", "Aus"}, 0),
         new Widget.Option("hand", "Werkzeug", new String[]{"Aus", "An"}, 1)
      );
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(true, 2, 1, -6, 0);
   }

   private static List<ItemStack> stacks(Widget.Ctx c) {
      List<ItemStack> l = collect(c.d, c.opt("hand", 1) == 1);
      if (l.isEmpty() && c.editor) {
         l = collect(HudData.sample(), c.opt("hand", 1) == 1);
      }

      return l;
   }

   private static List<ItemStack> collect(HudData d, boolean hand) {
      List<ItemStack> l = new ArrayList<>();

      for (ItemStack s : d.armor()) {
         if (!s.isEmpty()) {
            l.add(s);
         }
      }

      if (hand && !d.main.isEmpty() && d.main.isDamageableItem()) {
         l.add(d.main);
      }

      if (!d.off.isEmpty() && d.off.isDamageableItem()) {
         l.add(d.off);
      }

      return l;
   }

   private static float fraction(ItemStack s) {
      return s.isDamageableItem() && s.getMaxDamage() > 0 ? Math.max(0.0F, 1.0F - (float)s.getDamageValue() / s.getMaxDamage()) : 1.0F;
   }

   private static String label(ItemStack s, int mode) {
      if (mode == 2) {
         return "";
      } else if (!s.isDamageableItem()) {
         return "—";
      } else {
         return mode == 1 ? String.valueOf(s.getMaxDamage() - s.getDamageValue()) : Math.round(fraction(s) * 100.0F) + "%";
      }
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      List<ItemStack> l = stacks(c);
      if (l.isEmpty()) {
         return size(0, 0);
      } else {
         int mode = c.opt("text", 0);
         int tw = mode == 2 ? 0 : c.tw(mode == 1 ? "2031" : "100%");
         int w = 67 + (tw > 0 ? 7 + tw : 0) + 8;
         return size(w, 6 + l.size() * 19 - 3 + 5);
      }
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      List<ItemStack> l = stacks(c);
      int mode = c.opt("text", 0);
      Glass.card(g, 0, 0, w, h, 7, c.op);

      for (int i = 0; i < l.size(); i++) {
         ItemStack s = l.get(i);
         int y = 6 + i * 19;
         float f = fraction(s);
         boolean low = s.isDamageableItem() && f < 0.15F;
         float p = low ? Glass.pulse(800L) : 0.0F;
         if (low) {
            Glass.roundRect(g, 3, y - 1, w - 6, 18, 4, Glass.withAlpha(-41870, Math.round(30.0F + 40.0F * p)));
         }

         g.item(s, 7, y);
         int bx = 29;
         int by = y + 6;
         int bw = 38;
         Glass.roundRect(g, bx, by, bw, 4, 2, 1342177280);
         if (s.isDamageableItem()) {
            int fw = Math.max(f > 0.0F ? 2 : 0, Math.round(bw * f));
            int col = f > 0.5F ? Glass.accent() : (f > 0.2F ? -11930 : -41870);
            Glass.roundGradientH(g, bx, by, fw, 4, 2, Glass.lerpColor(col, -16777216, 0.35F), col);
         } else {
            Glass.roundRect(g, bx, by, bw, 4, 2, Glass.withAlpha(Glass.accent(), 85));
         }

         if (mode != 2) {
            int tc = low ? Glass.lerpColor(-855048, -41870, 0.4F + 0.6F * p) : -5855048;
            c.textR(label(s, mode), w - 8, y + 4, tc);
         }
      }
   }
}
