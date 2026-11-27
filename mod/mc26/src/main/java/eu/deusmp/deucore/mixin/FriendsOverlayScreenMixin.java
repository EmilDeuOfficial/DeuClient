package eu.deusmp.deucore.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freundesliste: abgerundetes dunkles Panel mit duennem gruenem Rand statt des Vanilla-Rahmens. */
@Mixin(FriendsOverlayScreen.class)
public abstract class FriendsOverlayScreenMixin {
    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void deucore$panel(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        if (!DeuConfig.get().customButtons) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
            return;
        }
        Ui.themedPanel(g, x, y, w, h);
    }
}
