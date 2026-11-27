package eu.deusmp.deucore.screen;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.cosmetics.Cosmetics;
import eu.deusmp.deucore.cosmetics.DeuAvatarState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * "Dein Spieler" im Kosmetik-Menue: drehbare 3D-Figur mit Cape und Fluegeln. Die Figur wird ohne Spielfigur
 * aufgebaut, sieht also im Hauptmenue und in einer Welt genau gleich aus.
 */
final class PlayerPreview {
    private PlayerPreview() {}

    /** Hoehe der Szene in Bloecken (Figur mit etwas Luft) und Breite eines Fluegelpaars bei 100 % */
    private static final float SCENE_H = 2.6f, FIGURE_W = 1.5f, WINGS_W = 4.1f;

    /** yaw 0 = von hinten, 180 = von vorn */
    static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float yaw) {
        AvatarRenderState state = state(yaw);
        // Massstab: passt bei jeder Fluegelgroesse in die Flaeche; bis 100 % bleibt die Figur gleich gross
        boolean wings = !((DeuAvatarState) state).deucore$getWings().isEmpty();
        float width = wings ? Math.max(FIGURE_W, WINGS_W * Math.max(1f, DeuConfig.get().wingScale)) : FIGURE_W;
        float scale = Math.min((y1 - y0) / SCENE_H, (x1 - x0) / width);
        Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
        g.entity(state, scale, offset, new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), x0, y0, x1, y1);
    }

    private static AvatarRenderState state(float yaw) {
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;
        UUID uuid = mc.getUser().getProfileId();
        Cosmetics.Loadout loadout = Cosmetics.loadoutFor(uuid);

        PlayerSkin skin = Ui.selfSkin();
        ClientAsset.Texture cape = loadout.cape().isEmpty() ? null : Cosmetics.capeAssetFor(uuid, loadout.cape());
        if (cape != null) skin = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());

        AvatarRenderState state = new AvatarRenderState();
        state.entityType = EntityTypes.PLAYER;
        // eigene Kennung: die Ueberblendung der Fluegel laeuft getrennt von der Figur in der Welt
        state.id = Integer.MIN_VALUE;
        state.skin = skin;
        state.showCape = cape != null || skin.cape() != null;
        state.showHat = options.isModelPartEnabled(PlayerModelPart.HAT);
        state.showJacket = options.isModelPartEnabled(PlayerModelPart.JACKET);
        state.showLeftSleeve = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        state.showRightSleeve = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
        state.showLeftPants = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        state.showRightPants = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        state.mainArm = options.mainHand().get();
        state.bodyRot = yaw;
        state.scale = 1f;
        state.ageScale = 1f;
        state.pose = Pose.STANDING;
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.eyeHeight = 1.62f;
        state.ageInTicks = (System.currentTimeMillis() % 3_600_000L) / 50f;
        state.capeLean = 8f; // Cape haengt ruhig, leicht vom Ruecken ab
        state.lightCoords = 0xF000F0;

        DeuAvatarState deu = (DeuAvatarState) state;
        deu.deucore$setWings(loadout.wings());
        deu.deucore$setElytraPose(-1);
        return state;
    }
}
