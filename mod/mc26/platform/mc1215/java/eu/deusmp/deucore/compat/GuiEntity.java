package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bis 1.21.5: eine Spielfigur direkt in die Oberflaeche zeichnen (wie die Figur im Inventar). */
public final class GuiEntity {
    private GuiEntity() {}

    public static void submit(GuiGraphics graphics, EntityRenderState state, float scale, Vector3f offset, Quaternionf rotation,
                              Quaternionf camera, int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
        graphics.pose().pushPose();
        graphics.pose().translate((x0 + x1) / 2.0, (y0 + y1) / 2.0, 50.0);
        graphics.pose().scale(scale, scale, -scale);
        graphics.pose().translate(offset.x, offset.y, offset.z);
        graphics.pose().mulPose(rotation);
        graphics.flush();
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.overrideCameraOrientation(camera.conjugate(new Quaternionf()).rotateY((float) Math.PI));
        dispatcher.setRenderShadow(false);
        graphics.drawSpecial(buffers -> dispatcher.render(state, 0.0, 0.0, 0.0, graphics.pose(), buffers, 0xF000F0));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }
}
