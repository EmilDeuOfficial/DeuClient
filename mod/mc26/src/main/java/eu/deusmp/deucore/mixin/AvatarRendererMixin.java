package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.cosmetics.Cosmetics;
import eu.deusmp.deucore.cosmetics.DeuAvatarState;
import eu.deusmp.deucore.cosmetics.ElytraWings;
import eu.deusmp.deucore.cosmetics.WingModel;
import eu.deusmp.deucore.cosmetics.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deu-Cosmetics am Spieler:
 *  - Cape: wird in die Skin-Daten gesetzt → Minecrafts eigene Cape- (und Elytra-)Darstellung zeichnet es
 *  - Flügel: eigene Render-Ebene (WingsLayer), Auswahl wird im RenderState gemerkt; auf Wunsch ersetzen sie die Elytra
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void deucore$addWings(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).deucore$addLayer(new WingsLayer((AvatarRenderer<?>) (Object) this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void deucore$cosmetics(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        Cosmetics.Loadout l = Cosmetics.loadoutFor(avatar.getUUID());
        ((DeuAvatarState) state).deucore$setWings(l.wings());
        boolean wingsAsElytra = state.chestEquipment.is(Items.ELYTRA) && Cosmetics.wingsInsteadOfElytra(avatar.getUUID())
                && WingModel.get(l.wings()) != null;
        ((DeuAvatarState) state).deucore$setElytraPose(wingsAsElytra ? ElytraWings.pose(avatar) : -1);
        ClientAsset.Texture cape = l.cape().isEmpty() ? null : Cosmetics.capeAssetFor(avatar.getUUID(), l.cape());
        if (cape != null) {
            PlayerSkin skin = state.skin;
            state.skin = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
            state.showCape = !state.isInvisible;
        }
    }
}
