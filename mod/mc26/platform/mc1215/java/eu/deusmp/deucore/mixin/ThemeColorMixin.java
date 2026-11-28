package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.Theme;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Bis 1.21.5: Themefarbe an den Stellen, durch die jede Flaeche, jedes Bild und jeder Text der Oberflaeche laeuft. */
@Mixin(GuiGraphics.class)
public abstract class ThemeColorMixin {
    private static final String FILL = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V";
    private static final String GRADIENT = "fillGradient(Lcom/mojang/blaze3d/vertex/VertexConsumer;IIIIIII)V";
    private static final String BLIT = "inner" + "Blit(Ljava/util/function/Function;Lnet/minecraft/resources/ResourceLocation;IIIIFFFFI)V";
    private static final String TEXT = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I";
    private static final String PLAIN = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I";

    @ModifyVariable(method = FILL, at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int deucore$fillColor(int color) {
        return Theme.c(color);
    }

    @ModifyVariable(method = GRADIENT, at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int deucore$gradientTop(int color) {
        return Theme.c(color);
    }

    @ModifyVariable(method = GRADIENT, at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int deucore$gradientBottom(int color) {
        return Theme.c(color);
    }

    @ModifyVariable(method = BLIT, at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int deucore$blitColor(int color) {
        return Theme.c(color);
    }

    @ModifyVariable(method = {TEXT, PLAIN}, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int deucore$textColor(int color) {
        return Theme.c(color);
    }
}
