package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.screen.DivineTitleScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Ersetzt den Minecraft-Titelbildschirm durch das Divine-Hauptmenü (ab 26.x verwaltet Gui die Screens). */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen divinecore$replaceTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen && DivineConfig.get().customTitleScreen) return new DivineTitleScreen();
        return screen;
    }
}
