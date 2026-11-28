package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Map;

/** Bis 1.21.4: die Spielfigur direkt in die Oberflaeche zeichnen (wie die Figur im Inventar). */
public final class GuiEntity {
    private GuiEntity() {}

    /** Karte Armbreite -> Zeichner; sie ist privat und wird ueber ihren Inhalt gefunden */
    private static Map<?, ?> players;

    private static PlayerRenderer renderer(EntityRenderDispatcher dispatcher, PlayerRenderState state) {
        try {
            if (players == null) {
                for (Field field : EntityRenderDispatcher.class.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    if (field.get(dispatcher) instanceof Map<?, ?> map && map.values().stream().anyMatch(v -> v instanceof PlayerRenderer)
                            && map.keySet().stream().noneMatch(k -> k instanceof net.minecraft.world.entity.EntityType<?>)) {
                        players = map;
                        break;
                    }
                }
            }
            return players != null && players.get(state.skin.model()) instanceof PlayerRenderer renderer ? renderer : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static void submit(GuiGraphics graphics, PlayerRenderState state, float scale, Vector3f offset, Quaternionf rotation,
                              Quaternionf camera, int x0, int y0, int x1, int y1) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        PlayerRenderer renderer = renderer(dispatcher, state);
        if (renderer == null) return;
        graphics.enableScissor(x0, y0, x1, y1);
        graphics.pose().pushPose();
        graphics.pose().translate((x0 + x1) / 2.0, (y0 + y1) / 2.0, 50.0);
        graphics.pose().scale(scale, scale, -scale);
        graphics.pose().translate(offset.x, offset.y, offset.z);
        graphics.pose().mulPose(rotation);
        graphics.flush();
        Lighting.setupForEntityInInventory();
        dispatcher.overrideCameraOrientation(camera.conjugate(new Quaternionf()).rotateY((float) Math.PI));
        dispatcher.setRenderShadow(false);
        graphics.drawSpecial(buffers -> renderer.render(state, graphics.pose(), buffers, 0xF000F0));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }
}
