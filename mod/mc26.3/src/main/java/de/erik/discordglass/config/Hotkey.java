package de.erik.discordglass.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import org.lwjgl.sdl.SDLKeyboard;

public class Hotkey {
   public int key = -1;
   public boolean ctrl;
   public boolean shift;
   public boolean alt;

   public Hotkey() {
   }

   public Hotkey(int key, boolean ctrl, boolean shift, boolean alt) {
      this.key = key;
      this.ctrl = ctrl;
      this.shift = shift;
      this.alt = alt;
   }

   public boolean isBound() {
      return this.key >= 0;
   }

   public static int layoutKey(char c, int fallback) {
      try {
         int sc = SDLKeyboard.SDL_GetScancodeFromKey(Character.toLowerCase(c), null);
         return sc > 0 ? sc : fallback;
      } catch (Throwable var3) {
         return fallback;
      }
   }

   public static boolean isModifierKey(int key) {
      return key == 224 || key == 228 || key == 225 || key == 229 || key == 226 || key == 230;
   }

   public static boolean ctrlDown() {
      return InputConstants.isKeyDown(224) || InputConstants.isKeyDown(228);
   }

   public static boolean shiftDown() {
      return InputConstants.isKeyDown(225) || InputConstants.isKeyDown(229);
   }

   public static boolean altDown() {
      return InputConstants.isKeyDown(226) || InputConstants.isKeyDown(230);
   }

   public boolean isPressedNow() {
      return !this.isBound() ? false : InputConstants.isKeyDown(this.key) && this.ctrl == ctrlDown() && this.shift == shiftDown() && this.alt == altDown();
   }

   public boolean matches(int pressedKey, boolean c, boolean s, boolean a) {
      return this.isBound() && pressedKey == this.key && c == this.ctrl && s == this.shift && a == this.alt;
   }

   public String describe() {
      if (!this.isBound()) {
         return eu.deusmp.deucore.Tr.t("Nicht belegt");
      } else {
         StringBuilder sb = new StringBuilder();
         if (this.ctrl) {
            sb.append(eu.deusmp.deucore.Tr.t("Strg")).append(" + ");
         }

         if (this.shift) {
            sb.append("Shift + ");
         }

         if (this.alt) {
            sb.append("Alt + ");
         }

         String name;
         try {
            name = Type.KEYBOARD.getOrCreate(this.key).getDisplayName().getString();
         } catch (Throwable var4) {
            name = "#" + this.key;
         }

         sb.append(name.length() == 1 ? name.toUpperCase() : name);
         return sb.toString();
      }
   }
}
