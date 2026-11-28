package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.GoldButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bis 1.21.10 zeichnet renderWidget den Hintergrund selbst: dieser eine Aufruf wird durch den Deu-Stil ersetzt
 * (abgerundet, flach und neutral, beim Ueberfahren heller; GoldButton in Akzentfarbe).
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget {
    public AbstractButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    /** Beschriftung aller Buttons in der glatten Deu-Schrift (Inhalt bleibt unveraendert) */
    @Override
    public Component getMessage() {
        Component msg = super.getMessage();
        if (!DeuConfig.get().customButtons || !net.minecraft.network.chat.FontDescription.DEFAULT.equals(msg.getStyle().getFont())) return msg;
        return msg.copy().withStyle(st -> st.withFont(eu.deusmp.deucore.screen.Ui.uiFont()));
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIII)V"))
    private void deucore$sprite(GuiGraphics g, RenderPipeline pipeline, ResourceLocation sprite, int x, int y, int w, int h, int color) {
        if (!DeuConfig.get().customButtons) {
            g.blitSprite(pipeline, sprite, x, y, w, h, color);
            return;
        }
        int a = Mth.ceil(this.alpha * 255f);
        boolean hover = this.active && this.isHoveredOrFocused();
        boolean gold = ((Object) this) instanceof GoldButton gb && gb.isGold();

        int r = Math.min(4, h / 4);
        if (gold) {
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(hover ? 0x3CEB7E : 0x27D86A, a));
        } else if (!this.active) {
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(0x0C0D10, Math.min(a, 170)));
        } else {
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(hover ? 0x262A31 : 0x16181D, Math.min(a, hover ? 245 : 230)));
        }
    }
}
