package de.erik.glasshud.gui;

import com.mojang.blaze3d.platform.InputConstants;
import eu.deusmp.deucore.Fx;
import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.hud.HudRenderer;
import de.erik.glasshud.hud.widgets.Widget;
import de.erik.glasshud.hud.widgets.Widgets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class EditorScreen extends Screen {
   private static final int PW = 372;
   private static final int PH = 208;
   private static final int SNAP = 5;
   private static final int MARGIN = 6;
   private final Screen parent;
   private String selected;
   private final List<EditorScreen.Hit> hits = new ArrayList<>();
   private final Map<String, Float> anims = new HashMap<>();
   private long openedAt;
   private long lastFrame;
   private float dt;
   private int panelX = Integer.MIN_VALUE;
   private int panelY;
   private boolean draggingPanel;
   private int panelGrabX;
   private int panelGrabY;
   private String dragId;
   private int grabX;
   private int grabY;
   private final List<Integer> guideV = new ArrayList<>();
   private final List<Integer> guideH = new ArrayList<>();
   private EditorScreen.Hit slider;
   private boolean draggingSlider;
   private double lastMouseX;

   public EditorScreen(Screen parent) {
      super(Component.literal("Glass HUD"));
      this.parent = parent;
   }

   public static EditorScreen demo(Screen parent, String selected) {
      EditorScreen s = new EditorScreen(parent);
      s.selected = selected;
      return s;
   }

   protected void init() {
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }

      if (this.panelX == Integer.MIN_VALUE) {
         this.panelX = (this.width - 372) / 2;
         this.panelY = (this.height - 208) / 2;
      }

      this.clampPanel();
   }

   private void clampPanel() {
      this.panelX = Math.max(4, Math.min(this.width - 372 - 4, this.panelX));
      this.panelY = Math.max(4, Math.min(this.height - 208 - 4, this.panelY));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      HudConfig.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   private float anim(String key, boolean target, float speed) {
      float v = this.anims.getOrDefault(key, target ? 1.0F : 0.0F);
      float t = target ? 1.0F : 0.0F;
      v += (t - v) * Math.min(1.0F, this.dt * speed);
      if (Math.abs(t - v) < 0.002F) {
         v = t;
      }

      this.anims.put(key, v);
      return v;
   }

   private float hover(String key, boolean hovered) {
      return this.anim("h:" + key, hovered, 14.0F);
   }

   private static boolean in(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
      super.extractBackground(g, mx, my, pt);
      float a = Math.min(1.0F, (float)(System.currentTimeMillis() - this.openedAt) / 200.0F);
      g.fillGradient(0, 0, this.width, this.height, Glass.alpha(-1878521840, a), Glass.alpha(-1341650928, a));
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
      long now = System.currentTimeMillis();
      this.dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.lastMouseX = mx;
      this.hits.clear();
      this.slider = null;
      HudRenderer.renderAll(g, this.minecraft, true);
      this.drawWidgetOverlays(g, mx, my);
      String hint = "Ziehen  ·  Mausrad: Größe  ·  Rechtsklick: aus  ·  Pfeile: fein";
      int hw = Fx.width(this.font, hint) + 24;
      int hx = (this.width - hw) / 2;
      int hy = this.height - 50;
      Glass.card(g, hx, hy, hw, 17, 8, 0.9F);
      Fx.text(g, this.font, hint, hx + 12, hy + 5, -5855048, false);
      if (this.dragId == null) {
         this.drawPanel(g, mx, my, now);
      }

      super.extractRenderState(g, mx, my, pt);
   }

   private void drawWidgetOverlays(GuiGraphicsExtractor g, int mx, int my) {
      HudConfig cfg = HudConfig.get();
      boolean overPanel = in(mx, my, this.panelX, this.panelY, 372, 208);
      String hover = null;
      if (!overPanel && this.dragId == null) {
         for (int i = Widgets.ALL.size() - 1; i >= 0; i--) {
            Widget w = Widgets.ALL.get(i);
            HudRenderer.Rect r = HudRenderer.bounds(w.id);
            if (r != null && r.contains(mx, my)) {
               hover = w.id;
               break;
            }
         }
      }

      for (Widget w : Widgets.ALL) {
         HudRenderer.Rect r = HudRenderer.bounds(w.id);
         if (r != null) {
            boolean sel = w.id.equals(this.selected);
            boolean hov = w.id.equals(hover);
            float f = this.anim("sel:" + w.id, sel, 14.0F);
            float hf = this.hover("wh:" + w.id, hov);
            if (f > 0.01F) {
               Glass.glow(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, 8, Glass.accent(), 4, f);
               Glass.roundOutline(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, 8, Glass.alpha(Glass.accent(), f));
               int[][] cs = new int[][]{
                  {r.x() - 3, r.y() - 3}, {r.x() + r.w() + 1, r.y() - 3}, {r.x() - 3, r.y() + r.h() + 1}, {r.x() + r.w() + 1, r.y() + r.h() + 1}
               };

               for (int[] c : cs) {
                  g.fill(c[0], c[1], c[0] + 3, c[1] + 3, Glass.alpha(-1, f));
               }

               int pct = Math.round(cfg.w(w.id).scale * 100.0F);
               String label = w.name + "  " + pct + "%";
               int lw = Fx.width(this.font, label) + 14;
               int lx = Math.max(2, Math.min(this.width - lw - 2, r.x()));
               int ly = r.y() - 17 >= 2 ? r.y() - 17 : r.y() + r.h() + 5;
               Glass.card(g, lx, ly, lw, 13, 6, f);
               Fx.text(g, this.font, label, lx + 7, ly + 3, Glass.alpha(-855048, f), false);
            } else if (hf > 0.01F) {
               Glass.roundOutline(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, 8, Glass.withAlpha(16777215, Math.round(90.0F * hf)));
            }
         }
      }

      for (int x : this.guideV) {
         g.fill(x, 0, x + 1, this.height, Glass.withAlpha(Glass.accent(), 160));
      }

      for (int y : this.guideH) {
         g.fill(0, y, this.width, y + 1, Glass.withAlpha(Glass.accent(), 160));
      }
   }

   private void drawPanel(GuiGraphicsExtractor g, int mx, int my, long now) {
      HudConfig cfg = HudConfig.get();
      float open = Math.min(1.0F, (float)(now - this.openedAt) / 240.0F);
      float ease = 1.0F - (float)Math.pow(1.0F - open, 3.0);
      int px = this.panelX;
      int py = this.panelY + Math.round((1.0F - ease) * 8.0F);
      Glass.glow(g, px, py, 372, 208, 11, Glass.accent(), 6, 0.5F * ease);
      Glass.card(g, px, py, 372, 208, 11, 0.97F * ease);
      this.hit(px, py, 372, 28, null);
      Glass.roundGradient(g, px + 10, py + 7, 18, 18, 6, Glass.accent(), Glass.accentDim());
      Glass.icon(g, Glass.ICON_KEYS, px + 14, py + 11, -16053230);
      Fx.text(g, this.font, "Glass HUD", px + 35, py + 7, -855048, false);
      Fx.text(g, this.font, "Anzeigen per Maus anordnen", px + 35, py + 17, -9933956, false);
      int bx = px + 372 - 26;
      int by = py + 7;
      float hc = this.hover("close", in(mx, my, bx, by, 18, 18));
      Glass.roundRect(g, bx, by, 18, 18, 6, Glass.lerpColor(352321535, Glass.withAlpha(-41870, 192), hc));
      Glass.icon(g, Glass.ICON_CLOSE, bx + 6, by + 6, Glass.lerpColor(-5855048, -1, hc));
      this.hit(bx, by, 18, 18, this::onClose);
      Glass.roundGradientH(g, px + 10, py + 29, 352, 1, 0, 16777215, 822083583);
      int lx = px + 10;
      int ly = py + 36;
      this.section(g, "ANZEIGEN", lx, ly, 160);
      int ry = ly + 12;

      for (Widget w : Widgets.ALL) {
         HudConfig.W wc = cfg.w(w.id);
         boolean sel = w.id.equals(this.selected);
         float hv = this.hover("row:" + w.id, in(mx, my, lx, ry, 160, 15));
         float on = this.anim("on:" + w.id, wc.enabled, 14.0F);
         if (sel) {
            Glass.roundGradientH(g, lx, ry, 160, 15, 5, Glass.withAlpha(Glass.accent(), 72), Glass.withAlpha(Glass.accent(), 8));
         } else if (hv > 0.0F) {
            Glass.roundRect(g, lx, ry, 160, 15, 5, Glass.alpha(385875967, hv));
         }

         int ic = Glass.lerpColor(-9933956, Glass.accent(), on);
         Glass.icon(g, w.icon, lx + 6, ry + (15 - w.icon.length) / 2, ic);
         Fx.text(g, this.font, w.name, lx + 21, ry + 4, Glass.lerpColor(-5855048, -855048, Math.max(on, hv)), false);
         this.toggle(g, lx + 160 - 26, ry + 2, on);
         String id = w.id;
         this.hits.add(new EditorScreen.Hit(lx, ry, 160, 15, () -> {
            wc.enabled = !wc.enabled;
            if (wc.enabled) {
               this.selected = id;
            } else if (id.equals(this.selected)) {
               this.selected = null;
            }

            HudConfig.save();
         }, () -> this.selected = id));
         ry += 15;
      }

      int rx = px + 182;
      int rw = 180;
      int y = py + 36;
      this.section(g, "DESIGN", rx, y, rw);
      y += 13;

      for (int i = 0; i < HudConfig.ACCENT_COLORS.length; i++) {
         int cx = rx + i * 23;
         int col = HudConfig.ACCENT_COLORS[i];
         boolean sel = cfg.accent == i;
         float hv = this.hover("dot" + i, in(mx, my, cx, y, 18, 18));
         if (sel) {
            Glass.glow(g, cx, y, 18, 18, 9, col, 4, 1.0F);
         }

         Glass.roundRect(g, cx, y, 18, 18, 9, col);
         if (sel) {
            Glass.roundOutline(g, cx - 2, y - 2, 22, 22, 11, -1);
            Glass.icon(g, Glass.ICON_CHECK, cx + 6, y + 7, -16053230);
         } else if (hv > 0.0F) {
            Glass.roundOutline(g, cx - 1, y - 1, 20, 20, 10, Glass.withAlpha(16777215, Math.round(160.0F * hv)));
         }

         int idx = i;
         this.hit(cx, y, 18, 18, () -> {
            cfg.accent = idx;
            HudConfig.save();
         });
      }

      y += 25;
      this.sliderRow(g, rx, y, rw, "Deckkraft", cfg.opacity + "%", (cfg.opacity - 20) / 80.0F, frac -> cfg.opacity = Math.round(20.0F + frac * 80.0F));
      y += 19;
      this.toggleRow(g, mx, my, rx, y, rw, "Vanilla-Effekte ausblenden", cfg.hideVanillaEffects, () -> cfg.hideVanillaEffects = !cfg.hideVanillaEffects);
      y += 22;
      Glass.roundGradientH(g, rx, y - 3, rw, 1, 0, 16777215, 687865855);
      Widget sw = this.selected == null ? null : Widgets.byId(this.selected);
      this.section(g, "AUSGEWÄHLT", rx, y, rw);
      if (sw == null) {
         String m = "Klicke eine Anzeige an,";
         Fx.text(g, this.font, m, rx, y + 16, -9933956, false);
         Fx.text(g, this.font, "um sie anzupassen.", rx, y + 26, -9933956, false);
      } else {
         HudConfig.W wc = cfg.w(sw.id);
         y += 12;
         Glass.icon(g, sw.icon, rx + 1, y + 3, Glass.accent());
         Fx.text(g, this.font, sw.name, rx + 15, y + 3, -855048, false);
         int rb = rx + rw - Fx.width(this.font, "Zurücksetzen") - 12;
         this.smallButton(g, mx, my, "reset", rb, y, Fx.width(this.font, "Zurücksetzen") + 12, 14, "Zurücksetzen", () -> {
            HudConfig.W d = sw.defaults();
            wc.enabled = d.enabled;
            wc.ax = d.ax;
            wc.ay = d.ay;
            wc.offX = d.offX;
            wc.offY = d.offY;
            wc.scale = 1.0F;
            wc.opts.clear();
            HudConfig.save();
         });
         y += 18;
         Fx.text(g, this.font, "Größe", rx + 2, y + 4, -5855048, false);
         int vx = rx + rw - 70;
         this.smallButton(g, mx, my, "sc-", vx, y, 16, 14, "-", () -> this.scale(wc, -0.1F));
         String pct = Math.round(wc.scale * 100.0F) + "%";
         Fx.text(g, this.font, pct, vx + 16 + (38 - Fx.width(this.font, pct)) / 2, y + 4, -855048, false);
         this.smallButton(g, mx, my, "sc+", vx + 54, y, 16, 14, "+", () -> this.scale(wc, 0.1F));
         y += 17;

         for (Widget.Option o : sw.options) {
            int cur = wc.opts.getOrDefault(o.key(), o.def());
            float hvx = this.hover("opt:" + o.key(), in(mx, my, rx, y, rw, 14));
            Glass.roundRect(g, rx, y, rw, 14, 5, Glass.lerpColor(218103807, 553648127, hvx));
            Fx.text(g, this.font, o.label(), rx + 6, y + 3, -5855048, false);
            String v = o.values()[Math.floorMod(cur, o.values().length)];
            boolean boolOpt = o.values().length == 2 && o.values()[0].equals("Aus");
            int vc = boolOpt ? (cur == 1 ? Glass.accent() : -9933956) : Glass.accent();
            Fx.text(g, this.font, v + " ›", rx + rw - Fx.width(this.font, v + " ›") - 6, y + 3, vc, false);
            String key = o.key();
            int n = o.values().length;
            this.hits.add(new EditorScreen.Hit(rx, y, rw, 14, () -> {
               wc.opts.put(key, Math.floorMod(wc.opts.getOrDefault(key, o.def()) + 1, n));
               HudConfig.save();
            }, () -> {
               wc.opts.put(key, Math.floorMod(wc.opts.getOrDefault(key, o.def()) - 1, n));
               HudConfig.save();
            }));
            y += 16;
         }
      }
   }

   private void scale(HudConfig.W wc, float delta) {
      wc.scale = Math.max(0.5F, Math.min(2.5F, Math.round((wc.scale + delta) * 10.0F) / 10.0F));
      HudConfig.save();
   }

   private void section(GuiGraphicsExtractor g, String text, int x, int y, int w) {
      Fx.text(g, this.font, text, x, y, -9933956, false);
      int lx = x + Fx.width(this.font, text) + 6;
      Glass.roundGradientH(g, lx, y + 4, x + w - lx, 1, 0, 822083583, 16777215);
   }

   private void toggle(GuiGraphicsExtractor g, int x, int y, float on) {
      int w = 20;
      int h = 11;
      if (on > 0.05F) {
         Glass.glow(g, x, y, w, h, 5, Glass.accent(), 3, on);
      }

      Glass.roundRect(g, x, y, w, h, 5, Glass.lerpColor(822083583, Glass.accent(), on));
      Glass.roundRect(g, x + 1 + Math.round(on * 9.0F), y + 1, 9, 9, 4, -1);
   }

   private void toggleRow(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean value, Runnable flip) {
      float hv = this.hover("t:" + label, in(mx, my, x, y, w, 16));
      float on = this.anim("tg:" + label, value, 14.0F);
      Glass.roundRect(g, x, y, w, 16, 5, Glass.lerpColor(218103807, 553648127, hv));
      Fx.text(g, this.font, Glass.ellipsize(this.font, label, w - 36), x + 6, y + 4, Glass.lerpColor(-5855048, -855048, Math.max(hv, on)), false);
      this.toggle(g, x + w - 26, y + 3, on);
      this.hit(x, y, w, 16, () -> {
         flip.run();
         HudConfig.save();
      });
   }

   private void sliderRow(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, float frac, Consumer<Float> set) {
      Glass.roundRect(g, x, y, w, 16, 5, 218103807);
      Fx.text(g, this.font, label, x + 6, y + 4, -5855048, false);
      Fx.text(g, this.font, value, x + w - Fx.width(this.font, value) - 6, y + 4, -855048, false);
      int sx = x + 6 + Fx.width(this.font, label) + 8;
      int sw = w - (sx - x) - Fx.width(this.font, "100%") - 14;
      int sy = y + 7;
      Glass.roundRect(g, sx, sy, sw, 3, 1, 822083583);
      Glass.roundGradientH(g, sx, sy, Math.max(2, Math.round(sw * frac)), 3, 1, Glass.accentDim(), Glass.accent());
      int kx = sx + Math.round(sw * frac);
      Glass.glow(g, kx - 4, sy - 3, 9, 9, 4, Glass.accent(), 2, 1.0F);
      Glass.roundRect(g, kx - 4, sy - 3, 9, 9, 4, -1);
      this.slider = new EditorScreen.Hit(sx - 4, y, sw + 8, 16, () -> {
         double rel = (this.lastMouseX - sx) / sw;
         set.accept((float)Math.max(0.0, Math.min(1.0, rel)));
         HudConfig.save();
      }, null);
      this.hits.add(this.slider);
   }

   private void smallButton(GuiGraphicsExtractor g, int mx, int my, String id, int x, int y, int w, int h, String label, Runnable action) {
      float hv = this.hover("sb:" + id, in(mx, my, x, y, w, h));
      Glass.roundRect(g, x, y, w, h, 5, Glass.lerpColor(385875967, Glass.withAlpha(Glass.accent(), 128), hv));
      Glass.roundOutline(g, x, y, w, h, 5, Glass.lerpColor(721420287, Glass.accent(), hv));
      Fx.text(g, this.font, label, x + (w - Fx.width(this.font, label)) / 2, y + (h - 8) / 2 + 1, -855048, false);
      this.hit(x, y, w, h, action);
   }

   private void hit(int x, int y, int w, int h, Runnable action) {
      this.hits.add(new EditorScreen.Hit(x, y, w, h, action, null));
   }

   private HudRenderer.Rect widgetAt(double mx, double my) {
      for (int i = Widgets.ALL.size() - 1; i >= 0; i--) {
         Widget w = Widgets.ALL.get(i);
         HudRenderer.Rect r = HudRenderer.bounds(w.id);
         if (r != null && r.contains(mx, my)) {
            this.dragId = w.id;
            return r;
         }
      }

      return null;
   }

   private void place(String id, int nx, int ny, int w, int h) {
      HudConfig.W wc = HudConfig.get().w(id);
      nx = Math.max(0, Math.min(this.width - w, nx));
      ny = Math.max(0, Math.min(this.height - h, ny));
      int cx = nx + w / 2;
      int cy = ny + h / 2;
      wc.ax = cx < this.width / 3 ? 0 : (cx > 2 * this.width / 3 ? 2 : 1);
      wc.ay = cy < this.height / 3 ? 0 : (cy > 2 * this.height / 3 ? 2 : 1);
      wc.offX = nx - wc.ax * (this.width - w) / 2;
      wc.offY = ny - wc.ay * (this.height - h) / 2;
   }

   private int[] snap(String id, int nx, int ny, int w, int h) {
      this.guideV.clear();
      this.guideH.clear();
      int bestDx = 6;
      int bestDy = 6;
      int sx = nx;
      int sy = ny;
      int gx = -1;
      int gy = -1;
      int[] xs = new int[]{6, (this.width - w) / 2, this.width - w - 6};

      for (int c : xs) {
         if (Math.abs(nx - c) < bestDx) {
            bestDx = Math.abs(nx - c);
            sx = c;
            gx = c == (this.width - w) / 2 ? this.width / 2 : -1;
         }
      }

      int[] ys = new int[]{6, (this.height - h) / 2, this.height - h - 6};

      for (int cx : ys) {
         if (Math.abs(ny - cx) < bestDy) {
            bestDy = Math.abs(ny - cx);
            sy = cx;
            gy = cx == (this.height - h) / 2 ? this.height / 2 : -1;
         }
      }

      for (Widget o : Widgets.ALL) {
         if (!o.id.equals(id)) {
            HudRenderer.Rect r = HudRenderer.bounds(o.id);
            if (r != null) {
               int[][] cxx = new int[][]{{r.x(), r.x()}, {r.x() + r.w() - w, r.x() + r.w()}, {r.x() + r.w() + 4, -1}, {r.x() - w - 4, -1}};

               for (int[] cxxx : cxx) {
                  if (Math.abs(nx - cxxx[0]) < bestDx) {
                     bestDx = Math.abs(nx - cxxx[0]);
                     sx = cxxx[0];
                     gx = cxxx[1];
                  }
               }

               int[][] cy = new int[][]{{r.y(), r.y()}, {r.y() + r.h() - h, r.y() + r.h()}, {r.y() + r.h() + 4, -1}, {r.y() - h - 4, -1}};

               for (int[] cxxxx : cy) {
                  if (Math.abs(ny - cxxxx[0]) < bestDy) {
                     bestDy = Math.abs(ny - cxxxx[0]);
                     sy = cxxxx[0];
                     gy = cxxxx[1];
                  }
               }
            }
         }
      }

      if (bestDx <= 5 && gx >= 0) {
         this.guideV.add(gx);
      }

      if (bestDy <= 5 && gy >= 0) {
         this.guideH.add(gy);
      }

      return new int[]{bestDx <= 5 ? sx : nx, bestDy <= 5 ? sy : ny};
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
      this.lastMouseX = e.x();
      boolean left = e.button() == 1;
      boolean right = e.button() == 3;
      boolean inPanel = in(e.x(), e.y(), this.panelX, this.panelY, 372, 208);
      if (inPanel) {
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            EditorScreen.Hit h = this.hits.get(i);
            if (h.contains(e.x(), e.y())) {
               Runnable r = right ? h.right() : (left ? h.left() : null);
               if (r != null) {
                  r.run();
                  if (h == this.slider) {
                     this.draggingSlider = true;
                  }

                  return true;
               }
            }
         }

         if (left && e.y() < this.panelY + 28) {
            this.draggingPanel = true;
            this.panelGrabX = (int)e.x() - this.panelX;
            this.panelGrabY = (int)e.y() - this.panelY;
         }

         return true;
      } else {
         HudRenderer.Rect r = this.widgetAt(e.x(), e.y());
         if (r != null) {
            this.selected = this.dragId;
            if (right) {
               HudConfig.get().w(this.dragId).enabled = false;
               HudConfig.save();
               this.selected = null;
               this.dragId = null;
            } else if (left) {
               this.grabX = (int)e.x() - r.x();
               this.grabY = (int)e.y() - r.y();
            }

            return true;
         } else {
            if (left) {
               this.selected = null;
            }

            return true;
         }
      }
   }

   public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
      this.lastMouseX = e.x();
      if (e.button() != 1) {
         return false;
      } else if (this.draggingSlider && this.slider != null) {
         this.slider.left().run();
         return true;
      } else if (this.draggingPanel) {
         this.panelX = (int)e.x() - this.panelGrabX;
         this.panelY = (int)e.y() - this.panelGrabY;
         this.clampPanel();
         return true;
      } else if (this.dragId != null) {
         HudRenderer.Rect r = HudRenderer.bounds(this.dragId);
         if (r != null) {
            int[] s = this.snap(this.dragId, (int)e.x() - this.grabX, (int)e.y() - this.grabY, r.w(), r.h());
            this.place(this.dragId, s[0], s[1], r.w(), r.h());
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(MouseButtonEvent e) {
      if (this.dragId != null || this.draggingPanel || this.draggingSlider) {
         HudConfig.save();
      }

      this.dragId = null;
      this.draggingPanel = false;
      this.draggingSlider = false;
      this.guideV.clear();
      this.guideH.clear();
      return true;
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (in(mx, my, this.panelX, this.panelY, 372, 208)) {
         return true;
      } else {
         for (int i = Widgets.ALL.size() - 1; i >= 0; i--) {
            Widget w = Widgets.ALL.get(i);
            HudRenderer.Rect r = HudRenderer.bounds(w.id);
            if (r != null && r.contains(mx, my)) {
               this.selected = w.id;
               this.scale(HudConfig.get().w(w.id), sy > 0.0 ? 0.1F : -0.1F);
               return true;
            }
         }

         return true;
      }
   }

   public boolean keyPressed(KeyEvent e) {
      if (e.key() == HudConfig.get().editorKey.key) {
         this.onClose();
         return true;
      } else {
         if (this.selected != null) {
            int step = e.hasShiftDown() ? 5 : 1;
            int dx = 0;
            int dy = 0;
            if (e.key() == InputConstants.KEY_LEFT) {
               dx = -step;
            } else if (e.key() == InputConstants.KEY_RIGHT) {
               dx = step;
            } else if (e.key() == InputConstants.KEY_UP) {
               dy = -step;
            } else if (e.key() == InputConstants.KEY_DOWN) {
               dy = step;
            }

            if (dx != 0 || dy != 0) {
               HudRenderer.Rect r = HudRenderer.bounds(this.selected);
               if (r != null) {
                  this.place(this.selected, r.x() + dx, r.y() + dy, r.w(), r.h());
                  HudConfig.save();
               }

               return true;
            }
         }

         return super.keyPressed(e);
      }
   }

   private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
      boolean contains(double mx, double my) {
         return mx >= this.x && my >= this.y && mx < this.x + this.w && my < this.y + this.h;
      }
   }
}
