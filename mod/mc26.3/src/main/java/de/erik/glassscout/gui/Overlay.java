package de.erik.glassscout.gui;

import eu.deusmp.deucore.Fx;
import de.erik.glassscout.config.ScoutConfig;
import de.erik.glassscout.scan.InvScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Overlay {
   public static final int PW = 146;
   private static ItemStack crystal;
   private static ItemStack totem;
   private static ItemStack xp;
   private static ItemStack gapple;
   private static ItemStack egapple;
   private static ItemStack pearl;
   private static ItemStack obsidian;
   private static ItemStack anchor;
   private static ItemStack glowstone;

   private Overlay() {
   }

   private static void init() {
      if (crystal == null) {
         crystal = new ItemStack(Items.END_CRYSTAL);
         totem = new ItemStack(Items.TOTEM_OF_UNDYING);
         xp = new ItemStack(Items.EXPERIENCE_BOTTLE);
         gapple = new ItemStack(Items.GOLDEN_APPLE);
         egapple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
         pearl = new ItemStack(Items.ENDER_PEARL);
         obsidian = new ItemStack(Items.OBSIDIAN);
         anchor = new ItemStack(Items.RESPAWN_ANCHOR);
         glowstone = new ItemStack(Items.GLOWSTONE);
      }
   }

   private static int heightFor(InvScan.Result r, boolean extras) {
      int h = 95;
      h += 74;
      if (extras && r.hasExtras()) {
         h += 6 + extraRows(r) * 18 + 4;
      }

      return h + 4;
   }

   private static int extraCount(InvScan.Result r) {
      int n = 0;
      if (r.gapples > 0) {
         n++;
      }

      if (r.egapples > 0) {
         n++;
      }

      if (r.pearls > 0) {
         n++;
      }

      if (r.obsidian > 0) {
         n++;
      }

      if (r.anchors > 0) {
         n++;
      }

      if (r.glowstone > 0) {
         n++;
      }

      return n;
   }

   private static int extraRows(InvScan.Result r) {
      return (extraCount(r) + 3) / 4;
   }

   private static int[] place(Screen screen, int pw, int ph) {
      int gx = (screen.width - 176) / 2;
      int gy = 0;
      int gw = 176;
      int gh = 166;
      if (screen instanceof AbstractContainerScreen) {
         ScreenRectangle r = screen.getRectangle();
         if (r.width() > 60 && r.width() < screen.width - 20) {
            gx = r.left();
            gw = r.width();
            gy = r.top();
            gh = r.height();
         } else {
            gy = (screen.height - 166) / 2;
         }
      }

      // Standard: links neben dem Menue, mit festem Abstand (klebt nicht am Menue); nur wenn links kein Platz ist, rechts daneben
      int gap = 14;
      int x = gx - pw - gap;
      if (x < 4) {
         x = gx + gw + gap;
         if (x + pw > screen.width - 4) {
            x = Math.max(4, screen.width - pw - 4);
         }
      }

      // senkrecht zum Menue zentriert (nicht buendig an der Oberkante)
      int menuH = gh;
      int y = Math.max(4, Math.min(screen.height - ph - 4, gy + (menuH - ph) / 2));
      return new int[]{x, y};
   }

   public static void draw(GuiGraphicsExtractor g, Minecraft mc, Screen screen, InvScan.Result r, String target) {
      if (!eu.deusmp.deucore.DeuConfig.get().scoutEnabled) {
         return;
      }
      init();
      ScoutConfig cfg = ScoutConfig.get();
      Font font = mc.font;
      boolean extras = cfg.showExtras && r.hasExtras();
      int ph = heightFor(r, cfg.showExtras);
      float zoom = de.erik.glasshud.hud.HudRenderer.snapScale(cfg.scale);
      int[] pos = place(screen, Math.round(146 * zoom), Math.round(ph * zoom));
      g.pose().pushMatrix();
      g.pose().translate(pos[0], pos[1]);
      g.pose().scale(zoom, zoom);
      int x = 0;
      int y = 0;
      float op = 1.0F - Math.max(0, Math.min(100, cfg.bgTransparency)) / 100.0F;
      // ruhiger Kasten ohne Rand, Leuchten und Farbverlauf: 1 = dunkel, 2 = schwarz, 0 = aus
      if (cfg.bgStyle != 0) {
         Glass.roundRect(g, x, y, 146, ph, 9, Math.round(0xE6 * op) << 24 | (cfg.bgStyle == 2 ? 0x000000 : 0x121418));
      }
      Glass.icon(g, Glass.ICON_EYE, x + 9, y + 11, Glass.accent());
      Fx.text(g, font, "Scout", x + 23, y + 9, -855048, false);
      if (target != null && !target.isEmpty()) {
         String t = Glass.ellipsize(font, target, 123 - Fx.width(font, "Scout") - 22);
         Fx.text(g, font, t, x + 146 - 9 - Fx.width(font, t), y + 9, Glass.accent(), false);
      }

      int cy = y + 22;
      Glass.roundGradientH(g, x + 8, cy, 130, 1, 0, 16777215, 889192447);
      cy += 6;
      cy = row(g, font, x, cy, crystal, "Kristalle", r.crystals, -34160);
      cy = row(g, font, x, cy, totem, "Totems", r.totems, -11930);
      cy = row(g, font, x, cy, xp, "XP-Flaschen", r.xp, -7536789);
      Glass.roundGradientH(g, x + 8, ++cy, 130, 1, 0, 16777215, 889192447);
      cy += 5;
      Fx.text(g, font, "RÜSTUNG", x + 9, cy, -9933956, false);
      if (r.lowest != null) {
         String w = "schwächstes Teil markiert";
         if (Fx.width(font, "RÜSTUNG") + Fx.width(font, w) + 24 < 146) {
            Fx.text(g, font, w, x + 146 - 9 - Fx.width(font, w), cy, -9933956, false);
         }
      }

      cy += 12;
      int cell = 28;
      int gx = x + (146 - cell * 4) / 2;

      for (int i = 0; i < 4; i++) {
         InvScan.Armor a = r.weakest[i];
         int cx = gx + i * cell;
         boolean low = a != null && a == r.lowest;
         Glass.inset(g, cx + 1, cy, cell - 2, 24, 5, op);
         if (low) {
            float p = Glass.pulse(1000L);
            // schwaechstes Teil: pulsierend eingefaerbte Kachel
            Glass.roundRect(g, cx + 1, cy, cell - 2, 24, 5, Glass.alpha(colorFor(a.fraction()), 0.25F + 0.25F * p));
         }

         if (a != null) {
            g.item(a.stack(), cx + (cell - 16) / 2, cy + 1);
            barH(g, cx + 5, cy + 19, cell - 10, 3, a.fraction());
         } else {
            Fx.text(g, font, "–", cx + (cell - Fx.width(font, "–")) / 2, cy + 8, -9933956, false);
         }
      }

      cy += 28;
      if (r.lowest != null) {
         InvScan.Armor ax = r.lowest;
         int col = colorFor(ax.fraction());
         String name = ax.stack().getHoverName().getString();
         g.item(ax.stack(), x + 8, cy + 2);
         int pct = Math.round(ax.fraction() * 100.0F);
         String ps = pct + "%";
         int pw = Math.round(Fx.width(font, ps) * 1.4F);
         Fx.text(g, font, Glass.ellipsize(font, name, 109), x + 28, cy, -855048, false);
         Fx.text(g, font, ax.left() + " / " + ax.max(), x + 28, cy + 13, -9933956, false);
         Glass.bigText(g, font, ps, x + 146 - 9 - pw, cy + 12, 1.4F, col);
      } else {
         String m = "Keine Rüstung gefunden";
         Fx.text(g, font, m, x + (146 - Fx.width(font, m)) / 2, cy + 6, -9933956, false);
      }

      cy += 29;
      if (extras) {
         Glass.roundGradientH(g, x + 8, cy - 2, 130, 1, 0, 16777215, 754974719);
         cy += 4;
         Object[][] list = new Object[][]{
            {gapple, r.gapples}, {egapple, r.egapples}, {pearl, r.pearls}, {obsidian, r.obsidian}, {anchor, r.anchors}, {glowstone, r.glowstone}
         };
         int i = 0;

         for (Object[] e : list) {
            int n = (Integer)e[1];
            if (n > 0) {
               int col = i % 4;
               int rowI = i / 4;
               int ex = x + 8 + col * 34;
               int ey = cy + rowI * 18;
               g.item((ItemStack)e[0], ex, ey);
               String s = String.valueOf(n);
               Fx.text(g, font, s, ex + 17, ey + 8, -5855048, false);
               i++;
            }
         }
      }

      g.pose().popMatrix();
   }

   private static int row(GuiGraphicsExtractor g, Font font, int x, int y, ItemStack icon, String label, int count, int stripe) {
      Glass.roundGradient(g, x + 6, y + 2, 2, 15, 1, stripe, Glass.lerpColor(stripe, -16777216, 0.5F));
      g.item(icon, x + 12, y + 1);
      Fx.text(g, font, label, x + 32, y + 5, count > 0 ? -5855048 : -9933956, false);
      String s = String.valueOf(count);
      int w = Math.round(Fx.width(font, s) * 1.4F);
      Glass.bigText(g, font, s, x + 146 - 9 - w, y + 3, 1.4F, count > 0 ? -1 : -9933956);
      return y + 21;
   }

   static int colorFor(float f) {
      return f > 0.6F ? 0xFF63E04A : (f > 0.3F ? -11930 : -41870);
   }

   private static void barH(GuiGraphicsExtractor g, int x, int y, int w, int h, float f) {
      Glass.roundRect(g, x, y, w, h, 1, 1610612736);
      int fw = Math.max(f > 0.0F ? 1 : 0, Math.round(w * f));
      if (fw > 0) {
         Glass.roundRect(g, x, y, fw, h, 1, colorFor(f));
      }
   }
}
