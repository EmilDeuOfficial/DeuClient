package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.1: Fortschritte-Fenster im DeuClient-Stil. */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin {
    @Redirect(method = "renderWindow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void deucore$window(GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
        if (!DeuConfig.get().customButtons) {
            g.blit(tex, x, y, u, v, w, h);
            return;
        }
        // Flaeche wurde schon vor dem Inhalt gezeichnet - hier nur noch der Rand ueber dem Inhalt
        DeuRender.roundOutline(g, x, y, w, h, 6, 0x802EFF7A);
    }

    /** Ueberschrift ueber dem Fenster in der DeuClient-Schrift */
    @ModifyArg(method = "init", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addTitleHeader(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/Font;)V"))
    private Component deucore$header(Component title) {
        return DeuConfig.get().customButtons ? title.copy().withStyle(st -> st.withFont(Ui.uiFont())) : title;
    }

    /** Dunkle Flaeche zuerst, damit Hintergrund und Advancements darueber sichtbar bleiben */
    @Inject(method = "renderInside", at = @At("HEAD"))
    private void deucore$panel(GuiGraphics g, int mouseX, int mouseY, int offsetX, int offsetY, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        Screen self = (Screen) (Object) this;
        DeuRender.roundRect(g, (self.width - 252) / 2, (self.height - 140) / 2, 252, 140, 6, 0xF2121418);
    }

    @Redirect(method = "renderWindow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int deucore$title(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        if (!DeuConfig.get().customButtons) return g.drawString(font, text, x, y, color, shadow);
        return g.drawString(Ui.font(), Ui.ui(text.getString()), x, y, Ui.TEXT, false);
    }
}
