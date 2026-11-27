package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuCore;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.ClientAsset;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Elytra mit DeuClient-Cape: duenn wie das Cape (Minecraft zeichnet sie vier Pixel dick, das wirkt mit einer
 * vollflaechigen Textur klobig). Andere Elytren bleiben unveraendert.
 */
@Mixin(ElytraModel.class)
public abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    /** Dicke der Fluegel: Anteil der urspruenglichen vier Pixel */
    private static final float THIN = 0.25f;
    /** Verschiebung entlang der Fluegeltiefe, damit die flachen Fluegel knapp hinter dem Ruecken liegen */
    private static final float SHIFT = 0.75f;

    // setupAnim setzt vorher die Ausgangspose wieder her, die Werte sammeln sich also nicht an
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void deucore$thin(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar) || avatar.skin == null) return;
        ClientAsset.Texture elytra = avatar.skin.elytra();
        if (elytra == null || !elytra.texturePath().getNamespace().equals(DeuCore.MOD_ID)) return;
        deucore$flatten(leftWing);
        deucore$flatten(rightWing);
    }

    private static void deucore$flatten(ModelPart wing) {
        Vector3f shift = new Quaternionf().rotationZYX(wing.zRot, wing.yRot, wing.xRot).transform(new Vector3f(0, 0, SHIFT));
        wing.x += shift.x;
        wing.y += shift.y;
        wing.z += shift.z;
        wing.zScale = THIN;
    }
}
