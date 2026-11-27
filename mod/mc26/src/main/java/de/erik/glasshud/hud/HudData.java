package de.erik.glasshud.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HudData {
   public static final HudData LIVE = new HudData();
   private static HudData sample;
   public boolean inWorld;
   public double x;
   public double y;
   public double z;
   public float yaw;
   public float pitch;
   public int fps;
   public final float[] fpsHist = new float[40];
   public int ping;
   public float speed;
   public ItemStack head = ItemStack.EMPTY;
   public ItemStack chest = ItemStack.EMPTY;
   public ItemStack legs = ItemStack.EMPTY;
   public ItemStack feet = ItemStack.EMPTY;
   public ItemStack main = ItemStack.EMPTY;
   public ItemStack off = ItemStack.EMPTY;
   public final List<HudData.Eff> effects = new ArrayList<>();
   public boolean kW;
   public boolean kA;
   public boolean kS;
   public boolean kD;
   public boolean kJump;
   public boolean kShift;
   public boolean kLmb;
   public boolean kRmb;
   public int cpsL;
   public int cpsR;
   public long clockTicks;
   private double px;
   private double py;
   private double pz;
   private long lastNanos;
   public long lastFpsSample;
   private boolean havePrev;
   private boolean wasL;
   private boolean wasR;
   private final ArrayDeque<Long> clicksL = new ArrayDeque<>();
   private final ArrayDeque<Long> clicksR = new ArrayDeque<>();
   private final Map<String, Integer> maxTicks = new HashMap<>();

   public ItemStack[] armor() {
      return new ItemStack[]{this.head, this.chest, this.legs, this.feet};
   }

   public boolean hasAnyArmor() {
      for (ItemStack s : this.armor()) {
         if (!s.isEmpty()) {
            return true;
         }
      }

      return !this.main.isEmpty() && this.main.isDamageableItem();
   }

   public void update(Minecraft mc) {
      Player p = mc.player;
      this.inWorld = p != null && mc.level != null;
      long nowN = System.nanoTime();
      long nowMs = System.currentTimeMillis();
      this.fps = mc.getFps();
      if (nowMs - this.lastFpsSample >= 250L) {
         this.lastFpsSample = nowMs;
         System.arraycopy(this.fpsHist, 1, this.fpsHist, 0, this.fpsHist.length - 1);
         this.fpsHist[this.fpsHist.length - 1] = this.fps;
      }

      Options o = mc.options;
      this.kW = o.keyUp.isDown();
      this.kA = o.keyLeft.isDown();
      this.kS = o.keyDown.isDown();
      this.kD = o.keyRight.isDown();
      this.kJump = o.keyJump.isDown();
      this.kShift = o.keyShift.isDown();
      this.kLmb = o.keyAttack.isDown();
      this.kRmb = o.keyUse.isDown();
      if (this.kLmb && !this.wasL) {
         this.clicksL.addLast(nowMs);
      }

      if (this.kRmb && !this.wasR) {
         this.clicksR.addLast(nowMs);
      }

      this.wasL = this.kLmb;
      this.wasR = this.kRmb;

      while (!this.clicksL.isEmpty() && nowMs - this.clicksL.peekFirst() > 1000L) {
         this.clicksL.pollFirst();
      }

      while (!this.clicksR.isEmpty() && nowMs - this.clicksR.peekFirst() > 1000L) {
         this.clicksR.pollFirst();
      }

      this.cpsL = this.clicksL.size();
      this.cpsR = this.clicksR.size();
      if (!this.inWorld) {
         this.havePrev = false;
         this.effects.clear();
         this.head = this.chest = this.legs = this.feet = this.main = this.off = ItemStack.EMPTY;
      } else {
         this.x = p.getX();
         this.y = p.getY();
         this.z = p.getZ();
         this.yaw = p.getYRot();
         this.pitch = p.getXRot();
         if (this.havePrev) {
            double dt = (nowN - this.lastNanos) / 1.0E9;
            if (dt > 0.0 && dt < 0.5) {
               double d = Math.hypot(this.x - this.px, this.z - this.pz);
               if (d < 20.0) {
                  this.speed = this.speed + (float)((d / dt - this.speed) * 0.12);
               }
            }
         }

         this.px = this.x;
         this.py = this.y;
         this.pz = this.z;
         this.lastNanos = nowN;
         this.havePrev = true;
         ClientPacketListener cp = mc.getConnection();
         if (cp != null) {
            PlayerInfo pi = cp.getPlayerInfo(p.getUUID());
            if (pi != null) {
               this.ping = pi.getLatency();
            }
         }

         this.head = p.getItemBySlot(EquipmentSlot.HEAD);
         this.chest = p.getItemBySlot(EquipmentSlot.CHEST);
         this.legs = p.getItemBySlot(EquipmentSlot.LEGS);
         this.feet = p.getItemBySlot(EquipmentSlot.FEET);
         this.main = p.getMainHandItem();
         this.off = p.getOffhandItem();
         this.clockTicks = mc.level.getOverworldClockTime();
         this.effects.clear();
         Set<String> seen = new HashSet<>();

         for (MobEffectInstance inst : p.getActiveEffects()) {
            Holder<MobEffect> h = inst.getEffect();
            String key = h.unwrapKey().map(k -> k.identifier().toString()).orElse("?") + "#" + inst.getAmplifier();
            seen.add(key);
            int ticks = inst.getDuration();
            Integer m = this.maxTicks.get(key);
            if (m == null || ticks > m) {
               m = ticks;
            }

            this.maxTicks.put(key, m);
            this.effects
               .add(
                  new HudData.Eff(
                     h,
                     effectName(h, inst.getAmplifier()),
                     inst.getAmplifier(),
                     ticks,
                     m,
                     ((MobEffect)h.value()).isBeneficial(),
                     ((MobEffect)h.value()).getColor() | 0xFF000000,
                     inst.isInfiniteDuration()
                  )
               );
         }

         this.maxTicks.keySet().retainAll(seen);
      }
   }

   static String effectName(Holder<MobEffect> h, int amp) {
      String n = ((MobEffect)h.value()).getDisplayName().getString();
      return amp <= 0 ? n : n + " " + roman(amp + 1);
   }

   static String roman(int n) {
      String[] r = new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
      return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
   }

   public static HudData sample() {
      if (sample != null) {
         return sample;
      } else {
         HudData d = new HudData();
         d.inWorld = true;
         d.x = 1234.5;
         d.y = 64.0;
         d.z = -523.2;
         d.yaw = 135.0F;
         d.pitch = 12.0F;
         d.fps = 144;

         for (int i = 0; i < d.fpsHist.length; i++) {
            d.fpsHist[i] = (float)(128.0 + 22.0 * Math.sin(i * 0.55) + 9.0 * Math.sin(i * 1.7));
         }

         d.ping = 28;
         d.speed = 5.6F;
         d.head = damaged(Items.DIAMOND_HELMET, 0.18F);
         d.chest = damaged(Items.NETHERITE_CHESTPLATE, 0.42F);
         d.legs = damaged(Items.IRON_LEGGINGS, 0.62F);
         d.feet = damaged(Items.GOLDEN_BOOTS, 0.93F);
         d.main = damaged(Items.DIAMOND_PICKAXE, 0.3F);
         d.off = ItemStack.EMPTY;
         d.effects.add(eff(MobEffects.SPEED, 1, 3080, 3600));
         d.effects.add(eff(MobEffects.NIGHT_VISION, 0, 6240, 9600));
         d.effects.add(eff(MobEffects.REGENERATION, 0, 140, 900));
         d.effects.add(eff(MobEffects.HUNGER, 0, 420, 600));
         d.kW = true;
         d.kJump = true;
         d.kLmb = true;
         d.cpsL = 7;
         d.cpsR = 2;
         d.clockTicks = 4200L;
         // ausserhalb einer Welt gibt es noch keine Item-Daten: Beispiel dann nicht merken
         if (net.minecraft.client.Minecraft.getInstance().level != null) {
            sample = d;
         }

         return d;
      }
   }

   private static ItemStack damaged(Item item, float used) {
      // vor dem Laden einer Welt sind Item-Komponenten nicht gebunden: new ItemStack wuerde das Spiel abstuerzen
      if (net.minecraft.client.Minecraft.getInstance().level == null) {
         return ItemStack.EMPTY;
      }

      ItemStack s = new ItemStack(item);
      if (s.isDamageableItem()) {
         s.setDamageValue(Math.round(s.getMaxDamage() * used));
      }

      return s;
   }

   private static HudData.Eff eff(Holder<MobEffect> h, int amp, int ticks, int max) {
      return new HudData.Eff(
         h, effectName(h, amp), amp, ticks, max, ((MobEffect)h.value()).isBeneficial(), ((MobEffect)h.value()).getColor() | 0xFF000000, false
      );
   }

   public record Eff(Holder<MobEffect> holder, String name, int amp, int ticks, int maxTicks, boolean beneficial, int color, boolean infinite) {
   }
}
