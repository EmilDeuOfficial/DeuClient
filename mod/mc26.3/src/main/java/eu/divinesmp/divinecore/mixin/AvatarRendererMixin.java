package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.cosmetics.Cosmetics;
import eu.divinesmp.divinecore.cosmetics.DivineAvatarState;
import eu.divinesmp.divinecore.cosmetics.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Divine-Cosmetics am Spieler:
 *  - Cape: wird in die Skin-Daten gesetzt → Minecrafts eigene Cape- (und Elytra-)Darstellung zeichnet es
 *  - Flügel: eigene Render-Ebene (WingsLayer), Auswahl wird im RenderState gemerkt
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void divinecore$addWings(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).divinecore$addLayer(new WingsLayer((AvatarRenderer<?>) (Object) this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void divinecore$cosmetics(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        Cosmetics.Loadout l = Cosmetics.loadoutFor(avatar.getUUID());
        ((DivineAvatarState) state).divinecore$setWings(l.wings());
        ClientAsset.Texture cape = l.cape().isEmpty() ? null : Cosmetics.capeAssetFor(avatar.getUUID(), l.cape());
        if (cape != null) {
            PlayerSkin skin = state.skin;
            state.skin = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
            state.showCape = !state.isInvisible;
        }
    }
}
