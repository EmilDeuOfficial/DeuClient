package eu.deusmp.deucore.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import eu.deusmp.deucore.DeuConfig;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bis 1.21.1: 3D-Fluegel am Ruecken des Spielers (wie in neueren Versionen, aber direkt aus der Spielfigur). */
public class WingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Pose der Fluegel, die die Elytra dieses Spielers ersetzen; -1 = keine */
    public static int elytraPose(AbstractClientPlayer player) {
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) || !Cosmetics.wingsInsteadOfElytra(player.getUUID())) return -1;
        return WingModel.get(Cosmetics.loadoutFor(player.getUUID()).wings()) != null ? ElytraWings.pose(player) : -1;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String wings = Cosmetics.loadoutFor(player.getUUID()).wings();
        if (wings == null || wings.isEmpty() || player.isInvisible()) return;
        int elytraPose = elytraPose(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        // mit Elytra keine zusaetzlichen Fluegel - ausser sie ersetzen die Elytra
        if (chest.is(Items.ELYTRA) && elytraPose < 0) return;
        draw(pose, buffers, light, getParentModel().body, wings, elytraPose, player.getFallFlyingTicks() > 4, player.isCrouching(),
                ageInTicks, player.getId(), chest.isEmpty() || chest.is(Items.ELYTRA));
    }

    /** Zeichnet die Fluegel an den Rumpf; auch fuer die Figur im Kosmetik-Menue */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, String wings, int elytraPose,
                            boolean flying, boolean crouching, float t, int id, boolean bareBack) {
        WingModel model = WingModel.get(wings);
        if (model == null) return; // Modell fehlt oder ist defekt (wurde einmal gemeldet)

        WingModel.Anim anim = new WingModel.Anim();
        anim.open = flying ? 55f : (crouching ? 16f : 6f); // in Ruhe eng am Koerper, nur im Flug weit gespreizt
        anim.time = t;
        if (DeuConfig.get().wingsAnimated) {
            anim.flapAmp = flying ? 24f : 5f;
            anim.flapPhase = t * (flying ? 0.8f : 0.1f);
            anim.swayAmp = flying ? 4f : 2.5f;
        }

        if (elytraPose >= 0 && model.hasClips(ElytraWings.CLIPS)) {
            // Blockbench-Animationen statt der prozeduralen Bewegung
            anim.clips = ElytraWings.CLIPS;
            anim.weights = ElytraWings.weights(id, elytraPose);
            anim.seconds = DeuConfig.get().wingsAnimated ? t / 20f : 0f;
        }

        // leichter Eigenglanz: Fluegel bleiben auch nachts sichtbar
        int blockLight = Math.max(light & 0xFFFF, 0xB0);
        final int glow = (light & 0xFFFF0000) | blockLight;
        RenderType type = RenderType.entityTranslucent(Cosmetics.wingsTexture(wings));

        pose.pushPose();
        body.translateAndRotate(pose);
        float scale = DeuConfig.get().wingScale; // Fluegelgroesse (Einstellung im Kosmetik-Menue)
        // Rueckseite des Koerpers bei z = 2/16, ein Brustpanzer traegt ein Pixel auf
        float back = bareBack ? 2f : 3f;
        pose.translate(0f, 3f / 16f, (back - model.contactDepth() * scale) / 16f);
        if (scale > 0.05f && Math.abs(scale - 1f) > 0.001f) pose.scale(scale, scale, scale);
        VertexConsumer vc = buffers.getBuffer(type);
        WingModel.QuadSink sink = (x, y, z, u, v, nx, ny, nz) ->
                vc.addVertex(x, y, z, 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, glow, nx, ny, nz);
        // beide Fluegel (rechts wie im Modell, links gespiegelt), nach Kameraabstand sortiert
        model.renderSorted(pose.last().pose(), anim, false, sink);
        pose.popPose();
    }
}
