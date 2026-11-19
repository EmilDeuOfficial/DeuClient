package eu.divinesmp.divinecore;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import eu.divinesmp.divinecore.mixin.WindowAccessor;
import eu.divinesmp.divinecore.net.DivineNetwork;
import eu.divinesmp.divinecore.screen.DivineModMenuScreen;
import eu.divinesmp.divinecore.screen.DivineTitleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * DivineCore (Minecraft 26.3) – Einstiegspunkt.
 *
 * Rein optisch: Ladebildschirm, Hauptmenü, Buttons, Hintergründe, Fenstertitel/-Icon
 * und das Divine-Menü (Rechts-Shift), in dem auch die Glass-Mods eingestellt werden.
 */
public class DivineCore implements ClientModInitializer {
    public static final String MOD_ID = "divinecore";
    private static double lastGuiScale = Double.NaN;
    public static final Logger LOGGER = LoggerFactory.getLogger("DivineCore");

    private static boolean menuKeyWasDown;

    @Override
    public void onInitializeClient() {
        DivineConfig.load();
        DivineNetwork.init();
        Crosshair.register();
        LOGGER.info("✦ DivineCore geladen – Rechts-Shift öffnet das Mod-Menü");
    }

    /** Wird jeden Client-Tick aufgerufen (MinecraftMixin): Rechts-Shift öffnet das Divine-Menü. */
    public static void onClientTick(Minecraft mc) {
        // GUI-Skalierung geaendert: Fenstergroesse neu berechnen
        double wanted = DivineConfig.guiScaleWanted();
        if (lastGuiScale != wanted && !(Double.isNaN(lastGuiScale) && wanted == 0)) {
            mc.resizeGui();
        }
        lastGuiScale = wanted;
        Zoom.tick();
        DemoMode.tick(mc);
        boolean down = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        if (down && !menuKeyWasDown) {
            Screen current = mc.gui.screen();
            boolean allowed = current == null || current instanceof TitleScreen || current instanceof DivineTitleScreen || current instanceof PauseScreen;
            if (allowed) mc.gui.setScreen(new DivineModMenuScreen(current));
        }
        menuKeyWasDown = down;
    }

    /** Setzt das Divine-Logo als Fenster-Icon (26.3 nutzt SDL statt GLFW). */
    public static void applyWindowIcon(Window window) {
        try (InputStream in = DivineCore.class.getResourceAsStream("/assets/divinecore/icon/icon_64.png")) {
            if (in == null) return;
            try (NativeImage image = NativeImage.read(in)) {
                SDL_Surface surface = WindowAccessor.divinecore$createIconSurface(image);
                if (surface == null) return;
                SDLVideo.SDL_SetWindowIcon(window.handle(), surface);
                SDLSurface.SDL_DestroySurface(surface);
            }
        } catch (Throwable t) {
            LOGGER.warn("Fenster-Icon konnte nicht gesetzt werden: {}", t.toString());
        }
    }
}
