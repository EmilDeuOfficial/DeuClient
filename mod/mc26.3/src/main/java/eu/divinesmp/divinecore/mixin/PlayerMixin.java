package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.cosmetics.Cosmetics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Divine-Logo vor dem Namen von Divine-Spielern (Namensschild über dem Kopf). */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void divinecore$logo(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() || !DivineConfig.get().logoNames) return;
        if (Cosmetics.isDivineUser(self.getUUID()) && !Cosmetics.hasLogo(cir.getReturnValue())) {
            cir.setReturnValue(Cosmetics.withLogo(cir.getReturnValue()));
        }
    }
}
