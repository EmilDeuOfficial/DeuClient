package de.erik.glassscout.gui;

import de.erik.glassscout.config.ScoutConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Fx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Glass {
   public static final int TEXT = -855048;
   public static final int TEXT_DIM = -5855048;
   public static final int TEXT_MUTED = -9933956;
   public static final int GOOD = -10682467;
   public static final int WARN = -11930;
   public static final int BAD = -41870;
   public static final String[] ICON_PIN = new String[]{".#####.", "#######", "###.###", "#######", ".#####.", "..###..", "...#..."};
   public static final String[] ICON_GAUGE = new String[]{"..#####..", ".#.....#.", "#.....#.#", "#....#..#", "#...#...#", ".#.....#.", "..#####.."};
   public static final String[] ICON_SIGNAL = new String[]{"......##.", "......##.", "...##.##.", "...##.##.", "##.##.##.", "##.##.##.", "##.##.##."};
   public static final String[] ICON_SHIELD = new String[]{"#######", "#.....#", "#.....#", "#.....#", ".#...#.", ".#...#.", "..#.#..", "...#..."};
   public static final String[] ICON_FLASK = new String[]{"..###..", "...#...", "...#...", "..###..", ".#####.", "#######", "#######", ".#####."};
   public static final String[] ICON_KEYS = new String[]{
      ".........", "#########", "#.#.#.#.#", "#########", "##.#.#.##", "#########", "#..###..#", "#########", "........."
   };
   public static final String[] ICON_COMPASS = new String[]{
      "...###...", ".##...##.", ".#..#..#.", "#..###..#", "#.#####.#", "#..###..#", ".#..#..#.", ".##...##.", "...###..."
   };
   public static final String[] ICON_CLOCK = new String[]{
      "..#####..", ".#..#..#.", "#...#...#", "#...#...#", "#...###.#", "#.......#", ".#.....#.", "..#####.."
   };
   public static final String[] ICON_SPEED = new String[]{"#...#....", ".#...#...", "..#...#..", "...#...#.", "..#...#..", ".#...#...", "#...#...."};
   public static final String[] ICON_CLOSE = new String[]{"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};
   public static final String[] ICON_CHECK = new String[]{"......#", ".....#.", "#...#..", ".#.#...", "..#...."};
   public static final String[] ICON_EYE = new String[]{"..#####..", ".#..#..#.", "#..###..#", ".#..#..#.", "..#####.."};
   public static final String[] ICON_RESET = new String[]{"..###..", ".#...#.", "#.....#", "#....##", "#...#.#", ".#.....", "..###.."};

   private Glass() {
   }

   public static int accent() {
      return ScoutConfig.get().accentColor();
   }

   public static int accentDim() {
      return lerpColor(accent(), -16777216, 0.45F);
   }

   public static float opacity() {
      return ScoutConfig.get().opacity / 100.0F;
   }

   public static int alpha(int color, float a) {
      int base = color >>> 24 & 0xFF;
      int na = Math.max(0, Math.min(255, Math.round(base * a)));
      return na << 24 | color & 16777215;
   }

   public static int withAlpha(int rgb, int a) {
      return Math.max(0, Math.min(255, a)) << 24 | rgb & 16777215;
   }

   public static int lerpColor(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24 & 0xFF;
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24 & 0xFF;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      return (int)(aa + (ba - aa) * t) << 24 | (int)(ar + (br - ar) * t) << 16 | (int)(ag + (bg - ag) * t) << 8 | (int)(ab + (bb - ab) * t);
   }

   public static float pulse(long periodMs) {
      double t = (double)(System.currentTimeMillis() % periodMs) / periodMs;
      return (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0));
   }

   private static int inset(int r, int row) {
      double dy = r - row - 0.5;
      return (int)Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
   }









   public static int iconW(String[] icon) {
      int w = 0;

      for (String s : icon) {
         w = Math.max(w, s.length());
      }

      return w;
   }



   public static int hsv(float h, float s, float v) {
      int i = (int)(h * 6.0F);
      float f = h * 6.0F - i;
      float p = v * (1.0F - s);
      float q = v * (1.0F - f * s);
      float t = v * (1.0F - (1.0F - f) * s);
      float r;
      float gg;
      float b;
      switch ((i % 6 + 6) % 6) {
         case 0:
            r = v;
            gg = t;
            b = p;
            break;
         case 1:
            r = q;
            gg = v;
            b = p;
            break;
         case 2:
            r = p;
            gg = v;
            b = t;
            break;
         case 3:
            r = p;
            gg = q;
            b = v;
            break;
         case 4:
            r = t;
            gg = p;
            b = v;
            break;
         default:
            r = v;
            gg = p;
            b = q;
      }

      return (int)(r * 255.0F) << 16 | (int)(gg * 255.0F) << 8 | (int)(b * 255.0F);
   }

   private static final java.util.IdentityHashMap<String[], String> ICONS = new java.util.IdentityHashMap<>();

   static {
      ICONS.put(ICON_PIN, "globe");
      ICONS.put(ICON_GAUGE, "hud");
      ICONS.put(ICON_SIGNAL, "server");
      ICONS.put(ICON_SHIELD, "person");
      ICONS.put(ICON_FLASK, "paint");
      ICONS.put(ICON_KEYS, "grid");
      ICONS.put(ICON_COMPASS, "globe");
      ICONS.put(ICON_CLOCK, "hourglass");
      ICONS.put(ICON_SPEED, "sparkle");
      ICONS.put(ICON_CLOSE, "close");
      ICONS.put(ICON_CHECK, "check");
      ICONS.put(ICON_EYE, "eye");
      ICONS.put(ICON_RESET, "edit");
   }

   public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
      DeuRender.roundRect(g, x, y, w, h, r, color);
   }

   public static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
      DeuRender.roundGradient(g, x, y, w, h, r, top, bottom);
   }

   public static void roundGradientH(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int left, int right) {
      if (w > 0 && h > 0) {
         r = Math.min(r, Math.min(w, h) / 2);
         DeuRender.roundRect(g, x, y, r * 2, h, r, left);
         DeuRender.roundRect(g, x + w - r * 2, y, r * 2, h, r, right);
         for (int i = r; i < w - r; i++) {
            g.fill(x + i, y, x + i + 1, y + h, lerpColor(left, right, (float)i / Math.max(1, w - 1)));
         }
      }
   }

   public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
      DeuRender.roundOutline(g, x, y, w, h, r, color);
   }

   public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color, int spread, float strength) {
      int base = color >>> 24 & 0xFF;
      for (int i = spread; i >= 1; i--) {
         float f = 1.0F - (float)(i - 1) / spread;
         int a = Math.round(base * 0.22F * f * f * strength);
         if (a > 0) {
            roundOutline(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, withAlpha(color, a));
         }
      }
   }

   public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float op) {
      roundRect(g, x, y, w, h, r, alpha(0xE60A0F0C, op));
      roundGradient(g, x, y, w, h, r, alpha(withAlpha(accent(), 34), op), alpha(withAlpha(accent(), 0), op));
      roundOutline(g, x, y, w, h, r, alpha(withAlpha(accent(), 110), op));
   }

   public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float op) {
      roundRect(g, x, y, w, h, r, alpha(0xB0050806, op));
      roundOutline(g, x, y, w, h, r, alpha(0x30FFFFFF, op));
   }

   public static void icon(GuiGraphicsExtractor g, String[] icon, int x, int y, int color) {
      String name = ICONS.get(icon);
      if (name != null) {
         Fx.icon(g, name, x, y, iconW(icon), icon.length, color);
         return;
      }
      for (int row = 0; row < icon.length; row++) {
         String line = icon[row];
         for (int col = 0; col < line.length(); col++) {
            if (line.charAt(col) == '#') {
               g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
         }
      }
   }

   public static String ellipsize(Font font, String s, int maxW) {
      return Fx.ellipsize(font, s, maxW);
   }

   public static void bigText(GuiGraphicsExtractor g, Font font, String s, int x, int y, float scale, int color) {
      g.pose().pushMatrix();
      g.pose().translate(x, y);
      g.pose().scale(scale, scale);
      Fx.text(g, font, s, 0, 0, color, false);
      g.pose().popMatrix();
   }
}
