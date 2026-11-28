package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Alle Info-Boxen beim Ueberfahren (Knoepfe, Items, Chat): dunkel, abgerundet, duenner Rand in der Themefarbe. */
// Fassung bis 1.21.5
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void deucore$box(GuiGraphicsExtractor g, int x, int y, int w, int h, int depth, Identifier style, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        // x, y, w, h umfassen nur den Inhalt
        g.pose().pushMatrix();
        g.pose().translateDepth(depth);
        DeuRender.roundRect(g, x - 5, y - 4, w + 10, h + 8, 5, 0xF6121418);
        DeuRender.roundOutline(g, x - 5, y - 4, w + 10, h + 8, 5, 0x802EFF7A);
        g.pose().popMatrix();
        ci.cancel();
    }
}
