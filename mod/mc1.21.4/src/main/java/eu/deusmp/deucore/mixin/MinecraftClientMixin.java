package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuCore;
import eu.deusmp.deucore.screen.DeuTitleScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * - Ersetzt das Minecraft-Titelmenü durch das Deu-Hauptmenü
 * - Fenstertitel "DeuClient - Minecraft <Version>"
 * - Deu-Fenster-Icon
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** Jedes Mal, wenn Minecraft das Titelmenü öffnen will, zeigen wir stattdessen das Deu-Menü. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen deucore$replaceTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen) {
            return new DeuTitleScreen();
        }
        return screen;
    }

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void deucore$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("DeuClient - Minecraft " + SharedConstants.getGameVersion().getName());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void deucore$windowIcon(RunArgs args, CallbackInfo ci) {
        DeuCore.applyWindowIcon(((MinecraftClient) (Object) this).getWindow());
    }
}
