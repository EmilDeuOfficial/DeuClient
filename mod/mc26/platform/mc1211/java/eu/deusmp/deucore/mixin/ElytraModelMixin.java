package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuCore;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.1: Elytra mit DeuClient-Cape flacher zeichnen. */
@Mixin(ElytraModel.class)
public abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    /** Dicke der Fluegel: Anteil der urspruenglichen vier Pixel */
    private static final float THIN = 0.25f;
    /** Verschiebung entlang der Fluegeltiefe, damit die flachen Fluegel knapp hinter dem Ruecken liegen */
    private static final float SHIFT = 0.75f;

    // setupAnim setzt hier x und z nicht zurueck: das Verschieben beginnt jedes Mal bei der Ausgangsposition
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void deucore$thin(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        boolean own = false;
        if (entity instanceof AbstractClientPlayer player) {
            ResourceLocation elytra = player.getSkin().elytraTexture();
            own = elytra != null && elytra.getNamespace().equals(DeuCore.MOD_ID);
        }
        // das Modell wird von allen Figuren geteilt: erst alles auf den Ausgangsstand
        deucore$restore(leftWing);
        deucore$restore(rightWing);
        if (!own) return;
        deucore$flatten(leftWing);
        deucore$flatten(rightWing);
    }

    private static void deucore$restore(ModelPart wing) {
        wing.x = wing.getInitialPose().x;
        wing.z = wing.getInitialPose().z;
        wing.zScale = 1f;
    }

    private static void deucore$flatten(ModelPart wing) {
        Vector3f shift = new Quaternionf().rotationZYX(wing.zRot, wing.yRot, wing.xRot).transform(new Vector3f(0, 0, SHIFT));
        wing.x += shift.x;
        wing.y += shift.y;
        wing.z += shift.z;
        wing.zScale = THIN;
    }
}
