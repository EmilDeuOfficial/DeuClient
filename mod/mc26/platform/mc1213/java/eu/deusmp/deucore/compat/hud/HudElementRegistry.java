package eu.deusmp.deucore.compat.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Bis 1.21.3 kennt die Fabric-API keine einzelnen HUD-Ebenen: Zusaetze kommen ueber die Anzeigen,
 * ersetzte Teile (Fadenkreuz, Wirkungs-Symbole) faengt HudLayerMixin ab.
 */
public final class HudElementRegistry {
    private HudElementRegistry() {}

    @FunctionalInterface
    public interface Element {
        void render(GuiGraphics g, DeltaTracker delta);
    }

    private static final Map<ResourceLocation, UnaryOperator<Element>> REPLACED = new HashMap<>();

    public static void attachElementBefore(ResourceLocation vanilla, ResourceLocation id, Element element) {
        HudRenderCallback.EVENT.register((g, delta) -> {
            if (!Minecraft.getInstance().options.hideGui) element.render(g, delta);
        });
    }

    public static void replaceElement(ResourceLocation vanilla, UnaryOperator<Element> replacement) {
        REPLACED.merge(vanilla, replacement, (first, second) -> original -> second.apply(first.apply(original)));
    }

    /** true, wenn dieses Teil ersetzt wurde (die Ersetzung ruft das Original selbst auf, falls sie es zeigen will) */
    public static boolean render(ResourceLocation vanilla, GuiGraphics g, DeltaTracker delta, Element original) {
        UnaryOperator<Element> replacement = REPLACED.get(vanilla);
        if (replacement == null) return false;
        replacement.apply(original).render(g, delta);
        return true;
    }
}
