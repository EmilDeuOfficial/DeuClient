package eu.deusmp.deucore.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import eu.deusmp.deucore.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Themefarbe an den Stellen, durch die jede Flaeche, jedes Bild und jeder Text der Oberflaeche laeuft. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ThemeColorMixin {
    private static final String FILL = "innerFill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"
            + "Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V";
    private static final String BLIT = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"
            + "Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V";
    private static final String TEXT = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V";

    @ModifyVariable(method = FILL, at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int deucore$fillColor(int color) {
        return Theme.c(color);
    }

    @ModifyVariable(method = FILL, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Integer deucore$gradientColor(Integer color) {
        return color == null ? null : Theme.c(color);
    }

    @ModifyVariable(method = BLIT, at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int deucore$blitColor(int color) {
        return Theme.c(color);
    }

    @ModifyVariable(method = TEXT, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int deucore$textColor(int color) {
        return Theme.c(color);
    }
}
