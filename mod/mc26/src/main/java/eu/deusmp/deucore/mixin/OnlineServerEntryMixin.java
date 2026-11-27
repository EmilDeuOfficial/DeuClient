package eu.deusmp.deucore.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Serverliste: gleicher gruener runder Start-Button wie bei den Einzelspieler-Welten statt des grauen Quadrats mit Pfeil. */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class OnlineServerEntryMixin {
    @Redirect(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void deucore$noGrayOverlay(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (!DeuConfig.get().customButtons) g.fill(x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void deucore$sprite(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        String path = sprite.getPath();
        if (!DeuConfig.get().customButtons || !path.startsWith("server_list/join")) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
            return;
        }
        Ui.joinButton(g, x, y, w, h, path.contains("highlighted"));
    }
}
