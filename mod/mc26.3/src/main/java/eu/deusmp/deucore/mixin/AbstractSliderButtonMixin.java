package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeichnet alle Schieberegler flach: dunkle Schiene, gedaempfte Fuellung bis zum schmalen Griff in der
 * Akzentfarbe, Beschriftung in der glatten UI-Schrift.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget.WithInactiveMessage {
    @Shadow protected double value;

    public AbstractSliderButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    @Shadow protected abstract void handleCursor(GuiGraphicsExtractor g);

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void deucore$slider(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        int a = Mth.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isHoveredOrFocused();
        int r = Math.min(4, h / 4);

        DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(!this.active ? 0x0C0D10 : hover ? 0x262A31 : 0x16181D, Math.min(a, 240)));

        // schmaler, runder Griff; die Beschriftung bleibt dahinter lesbar
        int kw = 5, kh = h - 6;
        int travel = Math.max(0, w - 6 - kw);
        int kx = x + 3 + (int) Math.round(Mth.clamp(this.value, 0.0, 1.0) * travel);
        if (this.active) { // flache, gedaempfte Fuellung bis zum Griff
            DeuRender.roundRect(g, x + 3, y + 3, kx + kw - (x + 3), kh, 2, DeuRender.argb(0x1B4D2B, a));
        }

        String label = getMessage().getString();
        int tw = Ui.width(label);
        g.text(Ui.font(), Ui.ui(label), x + (w - tw) / 2, y + (h - 8) / 2, this.active ? Ui.TEXT : Ui.TEXT_DIM, false);

        int knobColor = !this.active ? 0x5C6270 : hover ? 0x3CEB7E : 0x27D86A;
        DeuRender.roundRect(g, kx, y + 3, kw, kh, 2, DeuRender.argb(knobColor, a));

        if (this.isHovered) this.handleCursor(g);
        ci.cancel();
    }
}
