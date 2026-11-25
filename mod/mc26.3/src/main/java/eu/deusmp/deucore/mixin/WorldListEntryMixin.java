package eu.deusmp.deucore.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Weltliste: dunkler runder Schleier ueber dem Icon und ein gruener runder Start-Button statt des grauen Quadrats mit Pfeil. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry")
public abstract class WorldListEntryMixin {
    @Redirect(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void deucore$noGrayOverlay(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (!DeuConfig.get().customButtons) g.fill(x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void deucore$sprite(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        String path = sprite.getPath();
        boolean join = path.startsWith("world_list/join") || path.startsWith("world_list/marked_join");
        if (!DeuConfig.get().customButtons || !join) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
            return;
        }
        Ui.joinButton(g, x, y, w, h, path.contains("highlighted"));
    }
}
