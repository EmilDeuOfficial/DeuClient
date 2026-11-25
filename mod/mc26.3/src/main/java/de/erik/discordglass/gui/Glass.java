package de.erik.discordglass.gui;

import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.Fx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Glass {
   public static final int TEXT = -657926;
   public static final int TEXT_DIM = -6184523;
   public static final int TEXT_MUTED = -10592654;
   public static final int ACCENT = 0xFF2EFF7A;
   public static final int ACCENT_2 = 0xFF1FD35F;
   public static final int BLURPLE = -10983950;
   public static final int GREEN = -13904522;
   public static final int RED = -45730;
   public static final int YELLOW = -16059;
   public static final String[] ICON_MIC = new String[]{
      "...###...", "..#####..", "..#####..", "..#####..", "#.#####.#", "#..###..#", ".#.....#.", "..#####..", "....#....", "..#####.."
   };
   public static final String[] ICON_HEADPHONES = new String[]{
      "..#####..", ".#.....#.", "#.......#", "#.......#", "#.......#", "##.....##", "###...###", "###...###", "###...###", ".#.....#."
   };
   public static final String[] ICON_HANGUP = new String[]{
      ".........", ".........", "...###...", ".#######.", "####.####", "###...###", "##.....##", ".........", ".........", "........."
   };
   public static final String[] ICON_WAVE = new String[]{
      "....#....", "....#....", "..#.#.#..", "#.#.#.#.#", "#.#.#.#.#", "#.#.#.#.#", "#.#.#.#.#", "..#.#.#..", "....#....", "....#...."
   };
   public static final String[] ICON_SERVER = new String[]{
      "####.####", "####.####", "####.####", "####.####", ".........", "####.####", "####.####", "####.####", "####.####"
   };
   public static final String[] ICON_KEYBOARD = new String[]{
      ".........", "#########", "#.#.#.#.#", "#########", "##.#.#.##", "#########", "#..###..#", "#########", "........."
   };
   public static final String[] ICON_GEAR = new String[]{
      "...###...", ".#.###.#.", "#########", ".###.###.", "###...###", ".###.###.", "#########", ".#.###.#.", "...###..."
   };
   public static final String[] ICON_PLUG = new String[]{
      "..#...#..", "..#...#..", "..#...#..", "#########", ".#######.", "..#####..", "...###...", "....#....", "....#...."
   };
   public static final String[] ICON_CLOSE = new String[]{"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};
   public static final String[] ICON_SPEAKER = new String[]{"....#..", "...##.#", "####.#.", "####.#.", "####.#.", "...##.#", "....#.."};
   public static final String[] ICON_SEARCH = new String[]{".###...", "#...#..", "#...#..", "#...#..", ".###...", ".....#.", "......#"};
   public static final String[] ICON_CHECK = new String[]{"......#", ".....#.", "#...#..", ".#.#...", "..#...."};

   private Glass() {
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






   public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float opacity) {
      roundRect(g, x, y, w, h, r, alpha(-435812855, opacity));
      roundGradient(g, x, y, w, h, r, alpha(352321535, opacity), alpha(50331647, opacity));
      roundOutline(g, x, y, w, h, r, alpha(620756991, opacity));
      g.fill(x + r, y + 1, x + w - r, y + 2, alpha(486539263, opacity));
   }



   public static int iconW(String[] icon) {
      return icon[0].length();
   }

   public static void slash(GuiGraphicsExtractor g, int x, int y, int size, int color) {
      for (int i = 0; i < size; i++) {
         g.fill(x + i, y + i, x + i + 2, y + i + 1, -16777216);
         g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
      }
   }

   public static void dot(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
      roundRect(g, cx - r, cy - r, r * 2, r * 2, r, color);
   }



   public static void avatar(GuiGraphicsExtractor g, Font font, int x, int y, int size, String id, String name, boolean speaking) {
      int hv = id == null ? 0 : id.hashCode() * -1640531535;
      hv ^= hv >>> 15;
      int hue = Math.floorMod(hv, 360);
      int c1 = 0xFF000000 | hsv(hue / 360.0F, 0.65F, 0.95F);
      int c2 = 0xFF000000 | hsv((hue + 40) % 360 / 360.0F, 0.75F, 0.55F);
      if (speaking) {
         float p = pulse(900L);
         glow(g, x, y, size, size, size / 2, -13904522, 3, 0.6F + 0.4F * p);
         roundRect(g, x - 1, y - 1, size + 2, size + 2, (size + 2) / 2, -13904522);
      }

      roundGradient(g, x, y, size, size, size / 2, c1, c2);
      String ini = name != null && !name.isEmpty() ? name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase() : "?";
      Fx.text(g, font, ini, x + (size - Fx.width(font, ini)) / 2 + 1, y + (size - 8) / 2 + 1, -1, true);
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

   public static int pill(GuiGraphicsExtractor g, Font font, int rightX, int y, String text, int color) {
      int w = Fx.width(font, text) + 8;
      roundRect(g, rightX - w, y, w, 11, 5, withAlpha(color, 56));
      roundOutline(g, rightX - w, y, w, 11, 5, withAlpha(color, 112));
      Fx.text(g, font, text, rightX - w + 4, y + 2, lerpColor(color, -1, 0.35F), false);
      return rightX - w - 3;
   }

   public static int iconBadge(GuiGraphicsExtractor g, int rightX, int y, String[] icon, int color) {
      int w = iconW(icon);
      icon(g, icon, rightX - w, y, color);
      slash(g, rightX - w - 1, y - 1, Math.max(w, icon.length) + 1, color);
      return rightX - w - 4;
   }

   public static void signal(GuiGraphicsExtractor g, int x, int y, float ping) {
      int bars = ping < 0.0F ? 0 : (ping < 80.0F ? 3 : (ping < 160.0F ? 2 : 1));
      int color = bars == 3 ? -13904522 : (bars == 2 ? -16059 : -45730);

      for (int i = 0; i < 3; i++) {
         int h = 3 + i * 2;
         g.fill(x + i * 3, y + 7 - h, x + i * 3 + 2, y + 7, i < bars ? color : 822083583);
      }
   }

   private static final java.util.IdentityHashMap<String[], String> ICONS = new java.util.IdentityHashMap<>();

   static {
      ICONS.put(ICON_MIC, "chat");
      ICONS.put(ICON_HEADPHONES, "music");
      ICONS.put(ICON_HANGUP, "power");
      ICONS.put(ICON_WAVE, "hud");
      ICONS.put(ICON_SERVER, "server");
      ICONS.put(ICON_KEYBOARD, "grid");
      ICONS.put(ICON_GEAR, "gear");
      ICONS.put(ICON_PLUG, "sparkle");
      ICONS.put(ICON_CLOSE, "close");
      ICONS.put(ICON_SPEAKER, "music");
      ICONS.put(ICON_SEARCH, "search");
      ICONS.put(ICON_CHECK, "check");
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
      roundGradient(g, x, y, w, h, r, alpha(withAlpha(ACCENT, 34), op), alpha(withAlpha(ACCENT, 0), op));
      roundOutline(g, x, y, w, h, r, alpha(withAlpha(ACCENT, 110), op));
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
