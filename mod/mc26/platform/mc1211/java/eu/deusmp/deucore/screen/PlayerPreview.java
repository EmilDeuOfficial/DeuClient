package eu.deusmp.deucore.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.cosmetics.Cosmetics;
import eu.deusmp.deucore.cosmetics.WingsLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.UUID;

/**
 * "Dein Spieler" im Kosmetik-Menue bis 1.21.1: drehbare Figur mit Cape und Fluegeln. Das Spielermodell wird
 * ohne Spielfigur gezeichnet, sieht also im Hauptmenue und in einer Welt gleich aus.
 */
final class PlayerPreview {
    private PlayerPreview() {}

    /** Hoehe der Szene in Bloecken (Figur mit etwas Luft) und Breite eines Fluegelpaars bei 100 % */
    private static final float SCENE_H = 2.6f, FIGURE_W = 1.5f, WINGS_W = 4.1f;
    private static final int LIGHT = 0xF000F0;

    private static PlayerModel<AbstractClientPlayer> wide, slim;

    private static PlayerModel<AbstractClientPlayer> model(boolean thin) {
        PlayerModel<AbstractClientPlayer> model = thin ? slim : wide;
        if (model == null) {
            model = new PlayerModel<>(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(thin ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), thin);
            model.young = false;
            if (thin) slim = model;
            else wide = model;
        }
        return model;
    }

    /** yaw 0 = von hinten, 180 = von vorn */
    static void draw(GuiGraphics graphics, int x0, int y0, int x1, int y1, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;
        UUID uuid = mc.getUser().getProfileId();
        Cosmetics.Loadout loadout = Cosmetics.loadoutFor(uuid);
        PlayerSkin skin = Ui.selfSkin();
        ResourceLocation cape = loadout.cape().isEmpty() ? null : Cosmetics.capeAssetFor(uuid, loadout.cape());
        if (cape == null) cape = skin.capeTexture();

        // Massstab: passt bei jeder Fluegelgroesse in die Flaeche; bis 100 % bleibt die Figur gleich gross
        boolean wings = !loadout.wings().isEmpty();
        float width = wings ? Math.max(FIGURE_W, WINGS_W * Math.max(1f, DeuConfig.get().wingScale)) : FIGURE_W;
        float scale = Math.min((y1 - y0) / SCENE_H, (x1 - x0) / width);

        PlayerModel<AbstractClientPlayer> model = model(skin.model() == PlayerSkin.Model.SLIM);
        model.setAllVisible(true);
        model.hat.visible = options.isModelPartEnabled(PlayerModelPart.HAT);
        model.jacket.visible = options.isModelPartEnabled(PlayerModelPart.JACKET);
        model.leftSleeve.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);

        graphics.flush();
        graphics.enableScissor(x0, y0, x1, y1);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0, (y0 + y1) / 2.0, 50.0);
        pose.scale(scale, scale, -scale);
        pose.translate(0f, 0.9625f, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        // wie der Zeichner der Spielfigur: drehen, spiegeln, auf Spielergroesse, Fuesse auf den Boden
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0f, -1.501f, 0f);

        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), LIGHT, OverlayTexture.NO_OVERLAY, -1);
        if (cape != null && (loadout.cape().length() > 0 || options.isModelPartEnabled(PlayerModelPart.CAPE))) {
            pose.pushPose();
            pose.translate(0f, 0f, 0.125f);
            pose.mulPose(Axis.XP.rotationDegrees(10f)); // Cape haengt ruhig, leicht vom Ruecken ab
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            model.renderCloak(pose, buffers.getBuffer(RenderType.entitySolid(cape)), LIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (wings) {
            float age = (System.currentTimeMillis() % 3_600_000L) / 50f;
            // eigene Kennung: die Ueberblendung der Fluegel laeuft getrennt von der Figur in der Welt
            WingsLayer.draw(pose, buffers, LIGHT, model.body, loadout.wings(), -1, false, false, age, Integer.MIN_VALUE, true);
        }
        graphics.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }
}
