package de.erik.glassscout.scan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

public final class InvScan {
   private InvScan() {
   }

   public static InvScan.Result analyze(AbstractContainerScreen<?> screen, Minecraft mc) {
      InvScan.Result r = new InvScan.Result();

      for (Slot s : screen.getMenu().slots) {
         if (mc.player == null || s.container != mc.player.getInventory()) {
            r.slots++;
            ItemStack st = s.getItem();
            if (!st.isEmpty()) {
               r.items++;
               int n = st.getCount();
               if (st.is(Items.END_CRYSTAL)) {
                  r.crystals += n;
               } else if (st.is(Items.TOTEM_OF_UNDYING)) {
                  r.totems += n;
               } else if (st.is(Items.EXPERIENCE_BOTTLE)) {
                  r.xp += n;
               } else if (st.is(Items.GOLDEN_APPLE)) {
                  r.gapples += n;
               } else if (st.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                  r.egapples += n;
               } else if (st.is(Items.ENDER_PEARL)) {
                  r.pearls += n;
               } else if (st.is(Items.OBSIDIAN)) {
                  r.obsidian += n;
               } else if (st.is(Items.RESPAWN_ANCHOR)) {
                  r.anchors += n;
               } else if (st.is(Items.GLOWSTONE)) {
                  r.glowstone += n;
               }

               Equippable eq = (Equippable)st.get(DataComponents.EQUIPPABLE);
               if (eq != null && st.isDamageableItem() && st.getMaxDamage() > 0 && !st.is(Items.ELYTRA)) {
                  int idx = switch (eq.slot()) {
                     case HEAD -> 0;
                     case CHEST -> 1;
                     case LEGS -> 2;
                     case FEET -> 3;
                     default -> -1;
                  };
                  if (idx >= 0) {
                     int max = st.getMaxDamage();
                     int left = max - st.getDamageValue();
                     float f = Math.max(0.0F, (float)left / max);
                     InvScan.Armor a = new InvScan.Armor(st, eq.slot(), f, left, max);
                     if (r.weakest[idx] == null || a.fraction() < r.weakest[idx].fraction()) {
                        r.weakest[idx] = a;
                     }
                  }
               }
            }
         }
      }

      for (InvScan.Armor a : r.weakest) {
         if (a != null && (r.lowest == null || a.fraction() < r.lowest.fraction())) {
            r.lowest = a;
         }
      }

      return r;
   }

   public record Armor(ItemStack stack, EquipmentSlot slot, float fraction, int left, int max) {
   }

   public static final class Result {
      public int crystals;
      public int totems;
      public int xp;
      public int gapples;
      public int egapples;
      public int pearls;
      public int obsidian;
      public int anchors;
      public int glowstone;
      public int slots;
      public int items;
      public final InvScan.Armor[] weakest = new InvScan.Armor[4];
      public InvScan.Armor lowest;

      public boolean hasExtras() {
         return this.gapples + this.egapples + this.pearls + this.obsidian + this.anchors + this.glowstone > 0;
      }
   }
}
