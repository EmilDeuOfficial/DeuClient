package de.erik.glasshud.config;

import com.mojang.blaze3d.platform.InputConstants;
import eu.deusmp.deucore.compat.Platform;

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
      return Platform.layoutKey(c, fallback);
   }

   public static boolean isModifierKey(int key) {
      return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL || key == InputConstants.KEY_LSHIFT
         || key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT;
   }

   public static boolean ctrlDown() {
      return Platform.keyDown(InputConstants.KEY_LCONTROL) || Platform.keyDown(InputConstants.KEY_RCONTROL);
   }

   public static boolean shiftDown() {
      return Platform.keyDown(InputConstants.KEY_LSHIFT) || Platform.keyDown(InputConstants.KEY_RSHIFT);
   }

   public static boolean altDown() {
      return Platform.keyDown(InputConstants.KEY_LALT) || Platform.keyDown(InputConstants.KEY_RALT);
   }

   public boolean isPressedNow() {
      if (!this.isBound()) {
         return false;
      } else {
         return isModifierKey(this.key)
            ? Platform.keyDown(this.key)
            : Platform.keyDown(this.key) && this.ctrl == ctrlDown() && this.shift == shiftDown() && this.alt == altDown();
      }
   }

   public boolean matches(int pressedKey, boolean c, boolean s, boolean a) {
      return this.isBound() && pressedKey == this.key && c == this.ctrl && s == this.shift && a == this.alt;
   }

   public String describe() {
      if (!this.isBound()) {
         return "Nicht belegt";
      } else {
         StringBuilder sb = new StringBuilder();
         if (this.ctrl) {
            sb.append("Strg + ");
         }

         if (this.shift) {
            sb.append("Shift + ");
         }

         if (this.alt) {
            sb.append("Alt + ");
         }

         String name;
         try {
            name = Platform.keyName(this.key);
         } catch (Throwable var4) {
            name = "#" + this.key;
         }

         sb.append(name.length() == 1 ? name.toUpperCase() : name);
         return sb.toString();
      }
   }
}
