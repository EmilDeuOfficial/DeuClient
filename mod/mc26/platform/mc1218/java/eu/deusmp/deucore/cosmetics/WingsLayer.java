package eu.deusmp.deucore.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.deusmp.deucore.DeuConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

/**
 * 3D-Fluegel am Ruecken des Spielers. Das Modell (Blockbench, siehe {@link WingModel} und tools/wings/SPEC.md)
 * wird als rechter Fluegel erstellt, der linke ist gespiegelt. Am Koerper befestigt (folgt Schleichen/Drehen),
 * mit prozeduraler Animation: Oeffnungswinkel, Flap, flexende Armsegmente und wiegende Federn.
 */
public class WingsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    /**
     * Nur zum Testen: jede Flaeche immer doppelt zeichnen (umgekehrte Windung, negierte Normale).
     * Normal nicht noetig: entityTranslucent schneidet keine Rueckseiten weg und beleuchtet sie ueber gl_FrontFacing.
     * Der zweite Durchgang wird automatisch zugeschaltet, falls die Pipeline doch Rueckseiten wegschneidet.
     */
    private static final boolean FORCE_TWO_SIDED = false;

    public WingsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        String wings = ((DeuAvatarState) state).deucore$getWings();
        if (wings == null || wings.isEmpty() || state.isInvisible) return;
        int elytraPose = ((DeuAvatarState) state).deucore$getElytraPose();
        // mit Elytra keine zusätzlichen Flügel - ausser sie ersetzen die Elytra
        if (state.chestEquipment.is(Items.ELYTRA) && elytraPose < 0) return;

        WingModel model = WingModel.get(wings);
        if (model == null) return; // Modell fehlt oder ist defekt (wurde einmal gemeldet)

        boolean flying = state.fallFlyingTimeInTicks > 4;
        float t = state.ageInTicks;

        WingModel.Anim anim = new WingModel.Anim();
        anim.open = flying ? 55f : (state.isCrouching ? 16f : 6f); // in Ruhe eng am Koerper, nur im Flug weit gespreizt
        anim.time = t;
        if (DeuConfig.get().wingsAnimated) {
            anim.flapAmp = flying ? 24f : 5f;
            anim.flapPhase = t * (flying ? 0.8f : 0.1f);
            anim.swayAmp = flying ? 4f : 2.5f;
        }

        if (elytraPose >= 0 && model.hasClips(ElytraWings.CLIPS)) {
            // Blockbench-Animationen statt der prozeduralen Bewegung
            anim.clips = ElytraWings.CLIPS;
            anim.weights = ElytraWings.weights(state.id, elytraPose);
            anim.seconds = DeuConfig.get().wingsAnimated ? t / 20f : 0f;
        }

        // leichter Eigenglanz: Flügel bleiben auch nachts sichtbar
        int blockLight = Math.max(light & 0xFFFF, 0xB0);
        final int glow = (light & 0xFFFF0000) | blockLight;
        RenderType type = RenderType.entityTranslucent(Cosmetics.wingsTexture(wings));
        final boolean twoSided = FORCE_TWO_SIDED;

        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        float scale = DeuConfig.get().wingScale; // Fluegelgroesse (Einstellung im Kosmetik-Menue)
        // Rueckseite des Koerpers bei z = 2/16, ein Brustpanzer traegt ein Pixel auf; jedes Modell wird mit seiner
        // eigenen Ansatztiefe genau dort angelegt (Schulterblätter, 3/16 unter dem Hals)
        float back = state.chestEquipment.isEmpty() || state.chestEquipment.is(Items.ELYTRA) ? 2f : 3f;
        pose.translate(0f, 3f / 16f, (back - model.contactDepth() * scale) / 16f);
        if (scale > 0.05f && Math.abs(scale - 1f) > 0.001f) pose.scale(scale, scale, scale);
        VertexConsumer vc = buffers.getBuffer(type);
        WingModel.QuadSink sink = (x, y, z, u, v, nx, ny, nz) ->
                vc.addVertex(x, y, z, 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, glow, nx, ny, nz);
        // beide Fluegel (rechts wie im Modell, links gespiegelt), nach Kameraabstand sortiert
        model.renderSorted(pose.last().pose(), anim, twoSided, sink);
        pose.popPose();
    }
}
