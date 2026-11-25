package eu.deusmp.deucore.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.deusmp.deucore.DeuConfig;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

/**
 * 3D-Fluegel am Ruecken des Spielers. Das Modell (Blockbench, siehe {@link WingModel} und tools/wings/SPEC.md)
 * wird als rechter Fluegel erstellt, der linke ist gespiegelt. Am Koerper befestigt (folgt Schleichen/Drehen),
 * mit prozeduraler Animation: Oeffnungswinkel, Flap, flexende Armsegmente und wiegende Federn.
 */
public class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /**
     * Nur zum Testen: jede Flaeche immer doppelt zeichnen (umgekehrte Windung, negierte Normale).
     * Normal nicht noetig: entityTranslucent schneidet keine Rueckseiten weg und beleuchtet sie ueber gl_FrontFacing.
     * Der zweite Durchgang wird automatisch zugeschaltet, falls die Pipeline doch Rueckseiten wegschneidet.
     */
    private static final boolean FORCE_TWO_SIDED = false;

    public WingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        String wings = ((DeuAvatarState) state).deucore$getWings();
        if (wings == null || wings.isEmpty() || state.isInvisible) return;
        if (state.chestEquipment.is(Items.ELYTRA)) return; // mit Elytra keine zusätzlichen Flügel

        WingModel model = WingModel.get(wings);
        if (model == null) return; // Modell fehlt oder ist defekt (wurde einmal gemeldet)

        boolean flying = state.fallFlyingTimeInTicks > 4;
        float t = state.ageInTicks;

        WingModel.Anim anim = new WingModel.Anim();
        anim.open = flying ? 55f : (state.isCrouching ? 30f : 16f);
        anim.time = t;
        if (DeuConfig.get().wingsAnimated) {
            anim.flapAmp = flying ? 24f : 5f;
            anim.flapPhase = t * (flying ? 0.8f : 0.1f);
            anim.swayAmp = flying ? 4f : 2.5f;
        }

        // leichter Eigenglanz: Flügel bleiben auch nachts sichtbar
        int blockLight = Math.max(light & 0xFFFF, 0xB0);
        final int glow = (light & 0xFFFF0000) | blockLight;
        RenderType type = RenderTypes.entityTranslucent(Cosmetics.wingsTexture(wings));
        final boolean twoSided = FORCE_TWO_SIDED || type.pipeline().isCull();

        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        pose.translate(0f, 3f / 16f, 2.4f / 16f); // Schulterblätter, knapp hinter dem Rücken
        collector.submitCustomGeometry(pose, type, (p, vc) -> {
            WingModel.QuadSink sink = (x, y, z, u, v, nx, ny, nz) ->
                    vc.addVertex(x, y, z, 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, glow, nx, ny, nz);
            model.render(p.pose(), 1, anim, twoSided, sink);  // rechter Fluegel wie im Modell
            model.render(p.pose(), -1, anim, twoSided, sink); // linker Fluegel gespiegelt
        });
        pose.popPose();
    }
}
