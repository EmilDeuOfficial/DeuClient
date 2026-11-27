package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import de.erik.glasshud.hud.HudData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

public final class PotionsWidget extends Widget {
   private static final int ROW = 23;

   PotionsWidget() {
      super("potions", "Effekte", Glass.ICON_FLASK, new Widget.Option("bars", "Zeitbalken", new String[]{"Aus", "An"}, 1));
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(true, 2, 0, -6, 6);
   }

   private static List<HudData.Eff> effects(Widget.Ctx c) {
      List<HudData.Eff> src = c.d.effects;
      if (src.isEmpty() && c.editor) {
         src = HudData.sample().effects;
      }

      List<HudData.Eff> l = new ArrayList<>(src);
      l.sort(Comparator.<HudData.Eff, Boolean>comparing(e -> !e.beneficial()).thenComparing(e -> -e.ticks()));
      return l.size() > 8 ? l.subList(0, 8) : l;
   }

   private static String time(HudData.Eff e) {
      if (e.infinite()) {
         return "inf";
      } else {
         int s = e.ticks() / 20;
         return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
      }
   }

   private static Identifier sprite(Holder<MobEffect> h) {
      return h.unwrapKey().map(k -> {
         Identifier id = k.identifier();
         return Identifier.fromNamespaceAndPath(id.getNamespace(), "mob_effect/" + id.getPath());
      }).orElse(null);
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      List<HudData.Eff> l = effects(c);
      if (l.isEmpty()) {
         return size(0, 0);
      } else {
         int nameW = 60;
         int timeW = c.tw("0:00");

         for (HudData.Eff e : l) {
            nameW = Math.max(nameW, c.tw(e.name()));
            timeW = Math.max(timeW, c.tw(time(e)));
         }

         int w = 31 + nameW + 10 + timeW + 8;
         return size(Math.max(w, 128), 7 + l.size() * 23 - 3 + 4);
      }
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      List<HudData.Eff> l = effects(c);
      Glass.card(g, 0, 0, w, h, 7, c.op);
      boolean bars = c.opt("bars", 1) == 1;

      for (int i = 0; i < l.size(); i++) {
         HudData.Eff e = l.get(i);
         int y = 7 + i * 23;
         int secs = e.ticks() / 20;
         boolean low = !e.infinite() && secs < 10;
         float blink = low ? 0.55F + 0.45F * Glass.pulse(700L) : 1.0F;
         int tint = e.beneficial() ? e.color() : -41870;
         Glass.roundRect(g, 7, y, 18, 18, 5, Glass.withAlpha(tint, 56));
         Identifier sp = sprite(e.holder());
         if (sp != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sp, 7, y, 18, 18);
         }

         c.text(e.name(), 31, y + 1, Glass.alpha(-855048, blink));
         c.textR(time(e), w - 8, y + 1, low ? Glass.lerpColor(-855048, -41870, 0.5F + 0.5F * Glass.pulse(700L)) : -5855048);
         if (bars) {
            int bx = 31;
            int bw = w - 8 - bx;
            int by = y + 13;
            Glass.roundRect(g, bx, by, bw, 3, 1, 1073741824);
            float frac = e.infinite() ? 1.0F : Math.max(0.0F, Math.min(1.0F, (float)e.ticks() / Math.max(1, e.maxTicks())));
            int fw = Math.round(bw * frac);
            if (fw > 0) {
               int col = low ? -41870 : (e.beneficial() ? Glass.accent() : -41870);
               Glass.roundGradientH(g, bx, by, fw, 3, 1, Glass.alpha(Glass.lerpColor(col, -16777216, 0.35F), blink), Glass.alpha(col, blink));
            }
         }
      }
   }
}
