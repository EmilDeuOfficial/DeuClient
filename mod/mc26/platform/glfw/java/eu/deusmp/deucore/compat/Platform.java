package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Alles, was sich zwischen den Minecraft-Versionen unterscheidet: hier bis 26.2 (GLFW). */
public final class Platform {
    private Platform() {}

    /** Bis 26.2 fehlen die Text-Shader waehrend des ersten Ladens: dann nur den Balken zeichnen */
    public static final boolean TEXT_WHILE_LOADING = false;

    public static boolean keyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    /** Taste, die im aktuellen Tastaturlayout den Buchstaben schreibt */
    public static int layoutKey(char c, int fallback) {
        try {
            for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) {
                String name = GLFW.glfwGetKeyName(key, 0);
                if (name != null && name.equalsIgnoreCase(String.valueOf(c))) return key;
            }
        } catch (Throwable t) {
            return fallback;
        }
        return fallback;
    }

    public static String keyName(int key) {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    public static void setWindowIcon(Window window, InputStream png) throws IOException {
        byte[] bytes = png.readAllBytes();
        ByteBuffer file = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            file.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1), height = stack.mallocInt(1), channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(file, width, height, channels, 4);
            if (pixels == null) return;
            try {
                GLFWImage.Buffer icons = GLFWImage.malloc(1, stack);
                icons.position(0).width(width.get(0)).height(height.get(0)).pixels(pixels);
                GLFW.glfwSetWindowIcon(window.handle(), icons);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(file);
        }
    }

    public static Screen optionsScreen(Screen parent, Options options) {
        return new OptionsScreen(parent, options, Minecraft.getInstance().level != null);
    }
}
