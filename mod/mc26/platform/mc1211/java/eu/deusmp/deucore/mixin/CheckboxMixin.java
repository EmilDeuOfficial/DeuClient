package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bis 1.21.1: Checkboxen im DeuClient-Look. */
@Mixin(Checkbox.class)
public abstract class CheckboxMixin {
    @Shadow private boolean selected;

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void deucore$box(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        if (!DeuConfig.get().customButtons) {
            g.blitSprite(sprite, x, y, w, h);
            return;
        }
        boolean hover = sprite.getPath().contains("highlighted");
        DeuRender.roundRect(g, x, y, w, h, 3, hover || this.selected ? 0xFF2EFF7A : 0xFF3A3D44);
        DeuRender.roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, this.selected ? 0xFF1C5A2C : 0xFF121418);
        if (this.selected) Ui.icon(g, Ui.I_CHECK, x + 2, y + 2, w - 4, 0xFF7CF29C);
    }
}
