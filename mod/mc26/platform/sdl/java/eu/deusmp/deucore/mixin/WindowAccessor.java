package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDL_Surface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zugriff auf Minecrafts private Hilfsmethode, die aus einem Bild eine SDL-Icon-Fläche macht. */
@Mixin(Window.class)
public interface WindowAccessor {
    @Invoker("createIconSurface")
    static SDL_Surface deucore$createIconSurface(NativeImage image) {
        throw new AssertionError();
    }
}
