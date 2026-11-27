package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vollbild ist immer "Borderless" (randloses Fenster) statt exklusivem Vollbild. 26.1 kennt noch keine
 * Einstellung dafuer: statt den Monitor zu uebernehmen, wird das Fenster rahmenlos ueber ihn gelegt.
 */
@Mixin(Window.class)
public abstract class WindowFullscreenMixin {
    @Redirect(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V"))
    private void deucore$borderless(long window, long monitor, int x, int y, int width, int height, int refreshRate) {
        boolean fullscreen = monitor != 0L;
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, fullscreen ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowMonitor(window, 0L, x, y, width, height, GLFW.GLFW_DONT_CARE);
    }
}
