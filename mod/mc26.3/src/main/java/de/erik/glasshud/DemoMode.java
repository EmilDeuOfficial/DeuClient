package de.erik.glasshud;

import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.gui.EditorScreen;
import de.erik.glasshud.hud.HudData;
import de.erik.glasshud.hud.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class DemoMode {
   static final boolean ENABLED = Boolean.getBoolean("glasshud.demo");
   private static int ticks = -1;
   private static EditorScreen editor;
   private static int gx;
   private static int gy;

   private DemoMode() {
   }

   static void tick(Minecraft mc) {
      if (ENABLED && mc.level != null && mc.player != null) {
         ticks++;
         HudConfig cfg = HudConfig.get();
         switch (ticks) {
            case 1:
               HudRenderer.forceSample = true;
               cfg.accent = 0;
               cfg.opacity = 85;

               for (String id : new String[]{"coords", "fps", "ping", "speed", "compass", "clock", "potions", "armor", "keys"}) {
                  cfg.w(id).enabled = true;
               }

               mc.player.setXRot(-6.0F);
               mc.player.setYRot(mc.player.getYRot() + 130.0F);
               break;
            case 5:
               System.out
                  .println(
                     "[GlassHUD demo] Editor-Taste key="
                        + cfg.editorKey.key
                        + " (F8=65) beschreibung="
                        + cfg.editorKey.describe()
                        + " version="
                        + cfg.configVersion
                        + " accent="
                        + cfg.accent
                  );
               break;
            case 80:
               shot(mc, "01_hud");
               break;
            case 90:
               editor = EditorScreen.demo(null, "fps");
               mc.gui.setScreen(editor);
               break;
            case 130:
               shot(mc, "02_editor");
               break;
            case 135:
               HudRenderer.Rect r = HudRenderer.bounds("fps");
               if (r != null) {
                  gx = r.x() + 20;
                  gy = r.y() + 8;
                  editor.mouseClicked(ev(gx, gy), false);
               }
               break;
            case 140:
               editor.mouseDragged(ev(mc.getWindow().getGuiScaledWidth() / 2.0 + 18.0, 235.0), 0.0, 0.0);
               break;
            case 150:
               shot(mc, "03_dragging");
               break;
            case 155:
               editor.mouseReleased(ev(0.0, 0.0));
               HudConfig.W w = cfg.w("fps");
               System.out.println("[GlassHUD demo] fps nach dem Ziehen: ax=" + w.ax + " ay=" + w.ay + " offX=" + w.offX + " offY=" + w.offY);
               break;
            case 160:
               cfg.accent = 1;
               break;
            case 175:
               shot(mc, "04_editor_cyan");
               break;
            case 180:
               mc.gui.setScreen(null);
               break;
            case 200:
               System.out.println("[GlassHUD demo] Bildschirm nach dem Schliessen: " + mc.gui.screen());
               if (mc.gui.screen() != null) {
                  mc.gui.setScreen(null);
               }

               cfg.accent = 2;
               break;
            case 215:
               shot(mc, "05_hud_after");
               break;
            case 218:
               HudRenderer.forceSample = false;
               cfg.w("fps").ax = 0;
               cfg.w("fps").ay = 0;
               cfg.w("fps").offX = 6;
               cfg.w("fps").offY = 70;
               ItemStack helmet = new ItemStack(Items.IRON_HELMET);
               helmet.setDamageValue(60);
               mc.player.setItemSlot(EquipmentSlot.HEAD, helmet);
               mc.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
               mc.player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1900, 1));
               mc.player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 160, 0));
               break;
            case 245:
               HudData d = HudData.LIVE;
               System.out
                  .println(
                     "[GlassHUD demo] LIVE: inWorld="
                        + d.inWorld
                        + " x="
                        + (int)d.x
                        + " y="
                        + (int)d.y
                        + " z="
                        + (int)d.z
                        + " fps="
                        + d.fps
                        + " ping="
                        + d.ping
                        + " effekte="
                        + d.effects.size()
                        + " helm="
                        + !d.head.isEmpty()
                        + " brust="
                        + !d.chest.isEmpty()
                  );
               shot(mc, "06_live");
               break;
            case 255:
               mc.stop();
         }
      }
   }

   private static MouseButtonEvent ev(double x, double y) {
      return new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
   }

   private static void shot(Minecraft mc, String name) {
      Screenshot.grab(mc.gameDirectory, "glasshud_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
   }
}
