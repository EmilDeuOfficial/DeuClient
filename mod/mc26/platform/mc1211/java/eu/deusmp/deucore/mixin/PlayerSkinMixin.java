package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.cosmetics.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bis 1.21.1: das gewaehlte Cape (auch fuer die Elytra) steckt im Skin des Spielers. */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void deucore$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        String chosen = Cosmetics.loadoutFor(player.getUUID()).cape();
        if (chosen.isEmpty()) return;
        ResourceLocation cape = Cosmetics.capeAssetFor(player.getUUID(), chosen);
        if (cape == null) return;
        PlayerSkin skin = cir.getReturnValue();
        cir.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(), cape, cape, skin.model(), skin.secure()));
    }
}
