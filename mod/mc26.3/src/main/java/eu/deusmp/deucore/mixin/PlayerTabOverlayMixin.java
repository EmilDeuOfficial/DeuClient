package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.cosmetics.Cosmetics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deu-Logo vor Deu-Spielern in der Tab-Liste. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void deucore$logo(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!DeuConfig.get().logoNames) return;
        if (Cosmetics.isDeuUser(info.getProfile().id()) && !Cosmetics.hasLogo(cir.getReturnValue())) {
            cir.setReturnValue(Cosmetics.withLogo(cir.getReturnValue()));
        }
    }
}
