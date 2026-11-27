package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.screen.DeuTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ersetzt den Minecraft-Titelbildschirm durch das DeuClient-Hauptmenue (26.1: Minecraft verwaltet die Screens).
 * Auch setScreen(null) ohne Welt: Vanilla oeffnet dann selbst einen TitleScreen.
 */
@Mixin(Minecraft.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen deucore$replaceTitleScreen(Screen screen) {
        if (!DeuConfig.get().customTitleScreen) return screen;
        if (screen instanceof TitleScreen) return new DeuTitleScreen();
        if (screen == null && Minecraft.getInstance().level == null) return new DeuTitleScreen();
        return screen;
    }
}
