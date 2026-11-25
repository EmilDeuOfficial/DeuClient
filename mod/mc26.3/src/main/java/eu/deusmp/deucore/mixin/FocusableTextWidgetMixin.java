package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.FocusableTextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Textboxen (z. B. Warnhinweise) im DeuClient-Look: dunkle abgerundete Flaeche, dezenter gruener Rand. */
@Mixin(FocusableTextWidget.class)
public abstract class FocusableTextWidgetMixin {
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void deucore$fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (!DeuConfig.get().customButtons) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        DeuRender.roundRect(g, x1, y1, x2 - x1, y2 - y1, 4, 0xE60A0F0C);
    }

    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;outline(IIIII)V"))
    private void deucore$outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (DeuConfig.get().customButtons) DeuRender.roundOutline(g, x, y, w, h, 4, 0x802EFF7A);
        else g.outline(x, y, w, h, color);
    }
}
