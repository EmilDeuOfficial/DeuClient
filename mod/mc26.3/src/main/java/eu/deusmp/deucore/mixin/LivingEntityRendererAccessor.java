package eu.deusmp.deucore.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zugriff auf das geschützte addLayer, um die Flügel-Ebene an den Spieler-Renderer zu hängen. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @Invoker("addLayer")
    @SuppressWarnings("rawtypes")
    boolean deucore$addLayer(RenderLayer layer);
}
