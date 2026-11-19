package eu.divinesmp.divinecore.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import eu.divinesmp.divinecore.DivineConfig;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Divine-Galaxie-Flügel am Rücken des Spielers – pro Seite zwei Ebenen:
 * große obere Flügel (weit abgespreizt, Spitze nach oben) und kleine untere Flügel.
 * Am Körper befestigt (folgen Schleichen/Drehen), mit sanftem Flügelschlag.
 */
public class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Wurzel des Flügels in der Textur (u, v) – muss zu generate-ingame-ui.cjs passen */
    private static final float ROOT_U = 16f / 128f, ROOT_V = 0.44f;

    public WingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        String wings = ((DivineAvatarState) state).divinecore$getWings();
        if (wings == null || wings.isEmpty() || state.isInvisible) return;
        if (state.chestEquipment.is(Items.ELYTRA)) return; // mit Elytra keine zusätzlichen Flügel

        boolean flying = state.fallFlyingTimeInTicks > 4;
        float t = state.ageInTicks;
        float flap = DivineConfig.get().wingsAnimated
                ? (flying ? Mth.sin(t * 0.8f) * 24f : Mth.sin(t * 0.1f) * 5f)
                : 0f;
        float open = flying ? 55f : (state.isCrouching ? 30f : 16f);

        // leichter Eigenglanz: Galaxie-Flügel bleiben auch nachts sichtbar
        int blockLight = Math.max(light & 0xFFFF, 0xB0);
        final int glow = (light & 0xFFFF0000) | blockLight;
        RenderType upper = RenderTypes.entityTranslucent(Cosmetics.wingsTexture(wings));
        RenderType lower = RenderTypes.entityTranslucent(Cosmetics.wingsLowerTexture(wings));

        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        pose.translate(0f, 3f / 16f, 2.4f / 16f); // Schulterblätter, knapp hinter dem Rücken

        for (int side = -1; side <= 1; side += 2) {
            final int s = side;
            // obere Flügel: groß, leicht nach oben gekippt
            pose.pushPose();
            pose.translate(s * 1f / 16f, 0f, 0f);
            pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(s * -(open + flap))));
            pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(s * -10f))); // Spitze nach oben
            collector.submitCustomGeometry(pose, upper, (p, vc) -> wingQuad(p, vc, s, 28f / 16f, glow));
            pose.popPose();
            // untere Flügel: klein, weiter unten, etwas weniger abgespreizt
            pose.pushPose();
            pose.translate(s * 1f / 16f, 6.5f / 16f, 0.2f / 16f);
            pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(s * -(open * 0.8f + flap * 0.7f))));
            pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(s * 18f))); // Spitze nach unten
            collector.submitCustomGeometry(pose, lower, (p, vc) -> wingQuad(p, vc, s, 16f / 16f, glow));
            pose.popPose();
        }
        pose.popPose();
    }

    /** Quadratische, beidseitige Fläche; die Flügelwurzel der Textur liegt genau im Befestigungspunkt */
    private static void wingQuad(PoseStack.Pose p, VertexConsumer vc, int side, float size, int light) {
        float x0 = side * -ROOT_U * size, x1 = side * (1f - ROOT_U) * size;
        float y0 = -ROOT_V * size, y1 = (1f - ROOT_V) * size;
        Matrix4f m = p.pose();
        Vector3f n = p.transformNormal(0f, 0f, 1f, new Vector3f());
        vertex(vc, m, n, x0, y0, 0f, 0f, light);
        vertex(vc, m, n, x0, y1, 0f, 1f, light);
        vertex(vc, m, n, x1, y1, 1f, 1f, light);
        vertex(vc, m, n, x1, y0, 1f, 0f, light);
        Vector3f nb = p.transformNormal(0f, 0f, -1f, new Vector3f());
        vertex(vc, m, nb, x1, y0, 1f, 0f, light);
        vertex(vc, m, nb, x1, y1, 1f, 1f, light);
        vertex(vc, m, nb, x0, y1, 0f, 1f, light);
        vertex(vc, m, nb, x0, y0, 0f, 0f, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f n, float x, float y, float u, float v, int light) {
        Vector3f pos = m.transformPosition(x, y, 0f, new Vector3f());
        vc.addVertex(pos.x, pos.y, pos.z, 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
    }
}
