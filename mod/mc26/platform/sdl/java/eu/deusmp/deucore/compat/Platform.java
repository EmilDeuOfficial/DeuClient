package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import eu.deusmp.deucore.mixin.WindowAccessor;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;

import java.io.IOException;
import java.io.InputStream;

/** Alles, was sich zwischen den Minecraft-Versionen unterscheidet: hier ab 26.3 (SDL). */
public final class Platform {
    private Platform() {}

    public static boolean keyDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    /** Taste, die im aktuellen Tastaturlayout den Buchstaben schreibt */
    public static int layoutKey(char c, int fallback) {
        try {
            int sc = SDLKeyboard.SDL_GetScancodeFromKey(Character.toLowerCase(c), null);
            return sc > 0 ? sc : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static String keyName(int key) {
        return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
    }

    public static void setWindowIcon(Window window, InputStream png) throws IOException {
        try (NativeImage image = NativeImage.read(png)) {
            SDL_Surface surface = WindowAccessor.deucore$createIconSurface(image);
            if (surface == null) return;
            SDLVideo.SDL_SetWindowIcon(window.handle(), surface);
            SDLSurface.SDL_DestroySurface(surface);
        }
    }

    public static Screen optionsScreen(Screen parent, Options options) {
        return new OptionsScreen(parent, options);
    }
}
