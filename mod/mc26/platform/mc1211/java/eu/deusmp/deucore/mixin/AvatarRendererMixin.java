package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.cosmetics.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.1: Fluegel-Ebene an den Spieler-Zeichner haengen. */
@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void deucore$addWings(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).deucore$addLayer(new WingsLayer((PlayerRenderer) (Object) this));
    }
}
