package eu.divinesmp.divinecore;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * DivineCore – Einstiegspunkt der Mod.
 *
 * Die Mod ist rein optisch: eigener Ladebildschirm, eigenes Hauptmenü, Divine-Buttons,
 * eigene Hintergründe für Pause- und Multiplayer-Menü, Fenstertitel und Fenster-Icon.
 * Es werden keinerlei Spielmechaniken verändert.
 */
public class DivineCore implements ClientModInitializer {
    public static final String MOD_ID = "divinecore";
    public static final Logger LOGGER = LoggerFactory.getLogger("DivineCore");

    @Override
    public void onInitializeClient() {
        LOGGER.info("✦ DivineCore geladen – willkommen bei Divine!");
    }

    /**
     * Setzt das Divine-Logo als Fenster-Icon (Titelleiste + Taskleiste).
     * Wird direkt nach dem Erstellen des Spielfensters aufgerufen.
     */
    public static void applyWindowIcon(Window window) {
        String[] sizes = {"icon_64.png", "icon_32.png"};
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer icons = GLFWImage.malloc(sizes.length, stack);
            ByteBuffer[] pixels = new ByteBuffer[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                try (InputStream in = DivineCore.class.getResourceAsStream("/assets/divinecore/icon/" + sizes[i])) {
                    if (in == null) return;
                    byte[] bytes = in.readAllBytes();
                    ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
                    data.put(bytes).flip();
                    IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
                    pixels[i] = STBImage.stbi_load_from_memory(data, w, h, c, 4);
                    MemoryUtil.memFree(data);
                    if (pixels[i] == null) return;
                    icons.position(i).width(w.get(0)).height(h.get(0)).pixels(pixels[i]);
                }
            }
            icons.position(0);
            GLFW.glfwSetWindowIcon(window.getHandle(), icons);
            for (ByteBuffer p : pixels) if (p != null) STBImage.stbi_image_free(p);
        } catch (Throwable t) {
            // z. B. auf macOS nicht unterstützt – dann bleibt das Standard-Icon
            LOGGER.warn("Fenster-Icon konnte nicht gesetzt werden: {}", t.getMessage());
        }
    }
}
