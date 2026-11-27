package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.cosmetics.Cosmetics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deu-Logo vor dem Namen von Deu-Spielern (Namensschild über dem Kopf). */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void deucore$logo(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() || !DeuConfig.get().logoNames) return;
        if (Cosmetics.isDeuUser(self.getUUID()) && !Cosmetics.hasLogo(cir.getReturnValue())) {
            cir.setReturnValue(Cosmetics.withLogo(cir.getReturnValue()));
        }
    }
}
