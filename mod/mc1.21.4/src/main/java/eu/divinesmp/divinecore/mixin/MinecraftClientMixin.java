package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineCore;
import eu.divinesmp.divinecore.screen.DivineTitleScreen;
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
 * - Ersetzt das Minecraft-Titelmenü durch das Divine-Hauptmenü
 * - Fenstertitel "DeuClient - Minecraft <Version>"
 * - Divine-Fenster-Icon
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** Jedes Mal, wenn Minecraft das Titelmenü öffnen will, zeigen wir stattdessen das Divine-Menü. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen divinecore$replaceTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen) {
            return new DivineTitleScreen();
        }
        return screen;
    }

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void divinecore$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("DeuClient - Minecraft " + SharedConstants.getGameVersion().getName());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void divinecore$windowIcon(RunArgs args, CallbackInfo ci) {
        DivineCore.applyWindowIcon(((MinecraftClient) (Object) this).getWindow());
    }
}
