package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.cosmetics.Cosmetics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Divine-Logo vor Divine-Spielern in der Tab-Liste. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void divinecore$logo(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!DivineConfig.get().logoNames) return;
        if (Cosmetics.isDivineUser(info.getProfile().id()) && !Cosmetics.hasLogo(cir.getReturnValue())) {
            cir.setReturnValue(Cosmetics.withLogo(cir.getReturnValue()));
        }
    }
}
