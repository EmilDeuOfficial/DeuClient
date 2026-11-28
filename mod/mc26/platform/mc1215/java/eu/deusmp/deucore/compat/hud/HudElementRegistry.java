package eu.deusmp.deucore.compat.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.function.UnaryOperator;

/** Bis 1.21.5 heissen die HUD-Ebenen der Fabric-API anders: gleiche Aufrufe wie in neueren Versionen. */
public final class HudElementRegistry {
    private HudElementRegistry() {}

    @FunctionalInterface
    public interface Element {
        void render(GuiGraphics g, DeltaTracker delta);
    }

    public static void attachElementBefore(ResourceLocation vanilla, ResourceLocation id, Element element) {
        HudLayerRegistrationCallback.EVENT.register(layers ->
                layers.attachLayerBefore(vanilla, IdentifiedLayer.of(id, element::render)));
    }

    public static void replaceElement(ResourceLocation vanilla, UnaryOperator<Element> replacement) {
        HudLayerRegistrationCallback.EVENT.register(layers -> layers.replaceLayer(vanilla, original -> {
            Element element = replacement.apply(original::render);
            return IdentifiedLayer.of(original.id(), element::render);
        }));
    }
}
