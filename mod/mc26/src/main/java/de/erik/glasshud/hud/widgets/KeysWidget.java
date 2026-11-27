package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class KeysWidget extends Widget {
   private static final int K = 18;
   private static final int GAP = 2;
   private final float[] anim = new float[7];
   private long lastNanos;

   KeysWidget() {
      super(
         "keys",
         "Tasten",
         Glass.ICON_KEYS,
         new Widget.Option("mouse", "Maustasten", new String[]{"Aus", "An"}, 1),
         new Widget.Option("space", "Leertaste", new String[]{"Aus", "An"}, 1),
         new Widget.Option("cps", "Klicks/Sek.", new String[]{"Aus", "An"}, 1)
      );
   }

   @Override
   public HudConfig.W defaults() {
      return new HudConfig.W(false, 0, 2, 6, -6);
   }

   @Override
   public int[] measure(Widget.Ctx c) {
      int w = 70;
      int h = 44;
      if (c.opt("mouse", 1) == 1) {
         h += 20;
      }

      if (c.opt("space", 1) == 1) {
         h += 15;
      }

      if (c.opt("cps", 1) == 1) {
         h += 13;
      }

      return size(w, h + 6);
   }

   @Override
   public void draw(Widget.Ctx c, int w, int h) {
      GuiGraphicsExtractor g = c.g;
      Glass.card(g, 0, 0, w, h, 7, c.op);
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.016F : Math.min(0.1F, (float)(now - this.lastNanos) / 1.0E9F);
      this.lastNanos = now;
      boolean[] down = new boolean[]{c.d.kW, c.d.kA, c.d.kS, c.d.kD, c.d.kLmb, c.d.kRmb, c.d.kJump};

      for (int i = 0; i < this.anim.length; i++) {
         this.anim[i] = this.anim[i] + ((down[i] ? 1.0F : 0.0F) - this.anim[i]) * Math.min(1.0F, dt * 22.0F);
      }

      int x0 = 6;
      int y = 6;
      this.key(c, x0 + 18 + 2, y, 18, 18, "W", this.anim[0]);
      y += 20;
      this.key(c, x0, y, 18, 18, "A", this.anim[1]);
      this.key(c, x0 + 18 + 2, y, 18, 18, "S", this.anim[2]);
      this.key(c, x0 + 40, y, 18, 18, "D", this.anim[3]);
      y += 18;
      int inner = 58;
      if (c.opt("mouse", 1) == 1) {
         y += 2;
         int kw = (inner - 2) / 2;
         this.key(c, x0, y, kw, 18, "LMB", this.anim[4]);
         this.key(c, x0 + kw + 2, y, inner - kw - 2, 18, "RMB", this.anim[5]);
         y += 18;
      }

      if (c.opt("space", 1) == 1) {
         y += 2;
         // Taste 13 hoch (ungerade): der Strich liegt dann in der exakten Mitte
         this.key(c, x0, y, inner, 13, "", this.anim[6]);
         int lw = 22;
         int col = Glass.lerpColor(-9933956, -16053230, this.anim[6]);
         g.fill(x0 + (inner - lw) / 2, y + 6, x0 + (inner + lw) / 2, y + 7, col);
         y += 13;
      }

      if (c.opt("cps", 1) == 1) {
         y += 4;
         String s = c.d.cpsL + " | " + c.d.cpsR + " CPS";
         c.textC(s, w / 2.0F, y, -5855048);
      }
   }

   private void key(Widget.Ctx c, int x, int y, int kw, int kh, String label, float v) {
      GuiGraphicsExtractor g = c.g;
      Glass.inset(g, x, y, kw, kh, 4, c.op);
      if (v > 0.01F) {
         Glass.roundRect(g, x, y, kw, kh, 4, Glass.alpha(Glass.accent(), v));
      }

      if (!label.isEmpty()) {
         int tc = Glass.lerpColor(-5855048, -16053230, v);
         // exakt mittig: Gleitkomma-Breite, Ziffern-/Grossbuchstaben-Mitte liegt ca. 4,2 px unter dem Text-Ursprung
         c.textC(label, x + kw / 2.0F, y + kh / 2.0F - 4.2F, tc);
      }
   }
}
