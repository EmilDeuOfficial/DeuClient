package eu.deusmp.deucore;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import eu.deusmp.deucore.mixin.WindowAccessor;
import eu.deusmp.deucore.net.DeuNetwork;
import eu.deusmp.deucore.screen.DeuModMenuScreen;
import eu.deusmp.deucore.screen.DeuTitleScreen;
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
 * DeuCore (Minecraft 26.3) - Einstiegspunkt.
 *
 * Rein optisch: Ladebildschirm, Hauptmenü, Buttons, Hintergründe, Fenstertitel/-Icon
 * und das Deu-Menü (Rechts-Shift), in dem auch die Glass-Mods eingestellt werden.
 */
public class DeuCore implements ClientModInitializer {
    public static final String MOD_ID = "deucore";
    public static final Logger LOGGER = LoggerFactory.getLogger("DeuCore");

    private static boolean menuKeyWasDown;

    @Override
    public void onInitializeClient() {
        DeuConfig.load();
        DeuNetwork.init();
        Crosshair.register();
        applyModuleTheme();
        LOGGER.info("✦ DeuCore geladen - Rechts-Shift öffnet das Mod-Menü");
    }

    /** Setzt einmalig die gruene Akzentfarbe (Smaragd) bei HUD und Scout */
    private static void applyModuleTheme() {
        DeuConfig cfg = DeuConfig.get();
        if (cfg.moduleThemeApplied) return;
        for (GlassMods.GlassMod m : GlassMods.ALL) {
            if (!m.installed()) continue;
            for (GlassMods.Option o : m.options()) {
                if (!o.field().equals("accent")) continue;
                for (int i = 0; i < o.names().length; i++) {
                    if (o.names()[i].equals("Smaragd")) m.set(o, i);
                }
            }
        }
        cfg.moduleThemeApplied = true;
        DeuConfig.save();
    }

    /** Wird jeden Client-Tick aufgerufen (MinecraftMixin): Rechts-Shift öffnet das Deu-Menü. */
    public static void onClientTick(Minecraft mc) {
        Zoom.tick();
        DemoMode.tick(mc);
        boolean down = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        if (down && !menuKeyWasDown) {
            Screen current = mc.gui.screen();
            boolean allowed = current == null || current instanceof TitleScreen || current instanceof DeuTitleScreen || current instanceof PauseScreen;
            if (allowed) mc.gui.setScreen(new DeuModMenuScreen(current));
        }
        menuKeyWasDown = down;
    }

    /** Setzt das Deu-Logo als Fenster-Icon (26.3 nutzt SDL statt GLFW). */
    public static void applyWindowIcon(Window window) {
        try (InputStream in = DeuCore.class.getResourceAsStream("/assets/deucore/icon/icon_64.png")) {
            if (in == null) return;
            try (NativeImage image = NativeImage.read(in)) {
                SDL_Surface surface = WindowAccessor.deucore$createIconSurface(image);
                if (surface == null) return;
                SDLVideo.SDL_SetWindowIcon(window.handle(), surface);
                SDLSurface.SDL_DestroySurface(surface);
            }
        } catch (Throwable t) {
            LOGGER.warn("Fenster-Icon konnte nicht gesetzt werden: {}", t.toString());
        }
    }
}
