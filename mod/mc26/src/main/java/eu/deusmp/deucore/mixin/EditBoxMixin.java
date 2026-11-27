package eu.deusmp.deucore.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Textfelder im DeuClient-Look: dunkles, abgerundetes Feld, gruener Rand beim Fokus. */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void deucore$box(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        if (!DeuConfig.get().customButtons) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
            return;
        }
        boolean focused = ((AbstractWidget) (Object) this).isFocused();
        DeuRender.roundRect(g, x, y, w, h, 3, 0xE1121418);
        DeuRender.roundOutline(g, x, y, w, h, 3, focused ? 0xFF2EFF7A : 0x602E3138);
    }
}
