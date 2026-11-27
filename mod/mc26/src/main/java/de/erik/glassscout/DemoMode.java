package de.erik.glassscout;

import de.erik.glassscout.config.ScoutConfig;
import de.erik.glassscout.scan.InvScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class DemoMode {
   static final boolean ENABLED = Boolean.getBoolean("glassscout.demo");
   private static int ticks = -1;
   private static ContainerScreen current;

   private DemoMode() {
   }

   private static ItemStack stack(Item item, int count, float damaged) {
      ItemStack s = new ItemStack(item, count);
      if (damaged > 0.0F && s.isDamageableItem()) {
         s.setDamageValue(Math.round(s.getMaxDamage() * damaged));
      }

      return s;
   }

   private static SimpleContainer content() {
      SimpleContainer c = new SimpleContainer(54);
      c.setItem(0, stack(Items.END_CRYSTAL, 64, 0.0F));
      c.setItem(1, stack(Items.END_CRYSTAL, 32, 0.0F));
      c.setItem(2, stack(Items.TOTEM_OF_UNDYING, 1, 0.0F));
      c.setItem(3, stack(Items.TOTEM_OF_UNDYING, 1, 0.0F));
      c.setItem(4, stack(Items.TOTEM_OF_UNDYING, 1, 0.0F));
      c.setItem(5, stack(Items.EXPERIENCE_BOTTLE, 64, 0.0F));
      c.setItem(6, stack(Items.EXPERIENCE_BOTTLE, 37, 0.0F));
      c.setItem(7, stack(Items.GOLDEN_APPLE, 12, 0.0F));
      c.setItem(8, stack(Items.ENCHANTED_GOLDEN_APPLE, 3, 0.0F));
      c.setItem(9, stack(Items.ENDER_PEARL, 16, 0.0F));
      c.setItem(10, stack(Items.OBSIDIAN, 64, 0.0F));
      c.setItem(11, stack(Items.OBSIDIAN, 20, 0.0F));
      c.setItem(12, stack(Items.RESPAWN_ANCHOR, 5, 0.0F));
      c.setItem(13, stack(Items.DIAMOND_SWORD, 1, 0.3F));
      c.setItem(45, stack(Items.NETHERITE_HELMET, 1, 0.2F));
      c.setItem(46, stack(Items.DIAMOND_CHESTPLATE, 1, 0.35F));
      c.setItem(47, stack(Items.NETHERITE_LEGGINGS, 1, 0.82F));
      c.setItem(48, stack(Items.IRON_BOOTS, 1, 0.5F));
      c.setItem(49, stack(Items.DIAMOND_HELMET, 1, 0.05F));
      c.setItem(50, stack(Items.ELYTRA, 1, 0.9F));
      return c;
   }

   private static void open(Minecraft mc, String title) {
      ChestMenu menu = ChestMenu.sixRows(99, mc.player.getInventory(), content());
      current = new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title));
      mc.gui.setScreen(current);
   }

   private static void report(Minecraft mc, String label) {
      InvScan.Result r = InvScan.analyze(current, mc);
      String lowest = r.lowest == null ? "keine" : r.lowest.stack().getHoverName().getString() + " " + Math.round(r.lowest.fraction() * 100.0F) + "%";
      System.out
         .println(
            "[GlassScout demo] "
               + label
               + ": overlay="
               + GlassScoutClient.hasOverlay(current)
               + " | Kristalle="
               + r.crystals
               + " Totems="
               + r.totems
               + " XP="
               + r.xp
               + " | Gapples="
               + r.gapples
               + " EGapples="
               + r.egapples
               + " Perlen="
               + r.pearls
               + " Obsidian="
               + r.obsidian
               + " Anker="
               + r.anchors
               + " | schwächstes="
               + lowest
               + " | Slots="
               + r.slots
         );
   }

   static void tick(Minecraft mc) {
      if (ENABLED && mc.level != null && mc.player != null) {
         ticks++;
         ScoutConfig cfg = ScoutConfig.get();
         switch (ticks) {
            case 30:
               GlassScoutClient.simulateInvsee("Steve_PvP");
               open(mc, "Steve_PvP");
               break;
            case 60:
               report(mc, "A /invsee Steve_PvP");
               shot(mc, "01_invsee");
               break;
            case 65:
               mc.gui.setScreen(null);
               break;
            case 75:
               GlassScoutClient.forgetInvsee();
               cfg.mode = 0;
               open(mc, "Kiste");
               break;
            case 105:
               report(mc, "B normale Kiste, Modus 0 (soll KEIN Panel haben)");
               shot(mc, "02_kiste_aus");
               break;
            case 110:
               mc.gui.setScreen(null);
               cfg.mode = 1;
               break;
            case 115:
               open(mc, "Kiste");
               break;
            case 145:
               report(mc, "C normale Kiste, Modus 1 (soll Panel haben)");
               shot(mc, "03_kiste_modus1");
               break;
            case 150:
               mc.gui.setScreen(null);
               cfg.mode = 0;
               cfg.accent = 1;
               break;
            case 155:
               open(mc, "Inventory of Alex");
               break;
            case 185:
               report(mc, "D Titel 'Inventory of Alex' ohne Befehl, Modus 0 (soll Panel haben)");
               shot(mc, "04_titel_cyan");
               break;
            case 190:
               mc.stop();
         }
      }
   }

   private static void shot(Minecraft mc, String name) {
      Screenshot.grab(mc.gameDirectory, "glassscout_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
   }
}
