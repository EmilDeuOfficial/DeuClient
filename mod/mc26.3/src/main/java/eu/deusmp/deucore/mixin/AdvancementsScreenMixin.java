package eu.deusmp.deucore.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advancements-Fenster: abgerundeter dunkler Rahmen mit duennem gruenem Rand, Titel in heller DeuClient-Schrift. */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin {
    @Redirect(method = "extractWindow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void deucore$window(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v, int w, int h, int tw, int th) {
        if (!DeuConfig.get().customButtons) {
            g.blit(pipeline, tex, x, y, u, v, w, h, tw, th);
            return;
        }
        // Flaeche wurde schon vor dem Inhalt gezeichnet - hier nur noch der Rand ueber dem Inhalt
        DeuRender.roundOutline(g, x, y, w, h, 6, 0x802EFF7A);
    }

    /** Dunkle Flaeche zuerst, damit Hintergrund und Advancements darueber sichtbar bleiben */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void deucore$panel(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        Screen self = (Screen) (Object) this;
        DeuRender.roundRect(g, (self.width - 252) / 2, (self.height - 140) / 2, 252, 140, 6, 0xF20A0F0C);
    }

    @Redirect(method = "extractWindow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private void deucore$title(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, boolean shadow) {
        if (!DeuConfig.get().customButtons) {
            g.text(font, text, x, y, color, shadow);
            return;
        }
        g.text(Ui.font(), Ui.ui(text.getString()), x, y, Ui.TEXT, false);
    }
}
