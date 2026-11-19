package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineRender;
import eu.divinesmp.divinecore.screen.Ui;
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
 * Zeichnet alle Schieberegler im DeuClient-Stil: dunkle Pille als Schiene,
 * gruene Fuellung bis zum runden Griff, Beschriftung in der glatten UI-Schrift.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget.WithInactiveMessage {
    @Shadow protected double value;

    public AbstractSliderButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    @Shadow protected abstract void handleCursor(GuiGraphicsExtractor g);

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void divinecore$slider(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DivineConfig.get().customButtons) return;
        int a = Mth.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isHoveredOrFocused();
        int r = Math.min(4, h / 4);

        if (hover) DivineRender.roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, DivineRender.argb(0x22C55E, a));
        DivineRender.roundRect(g, x, y, w, h, r, DivineRender.argb(this.active ? 0x0A0F0C : 0x070A08, Math.min(a, 235)));

        // schmaler, runder Griff; die Beschriftung bleibt dahinter lesbar
        int kw = 7, kh = h - 4;
        int travel = Math.max(0, w - 4 - kw);
        int kx = x + 2 + (int) Math.round(Mth.clamp(this.value, 0.0, 1.0) * travel);
        if (this.active) {
            DivineRender.roundGradient(g, x + 2, y + 2, kx + kw - (x + 2), kh, Math.min(4, kh / 4), DivineRender.argb(0x16A34A, a), DivineRender.argb(0x14532D, a));
        }

        String label = getMessage().getString();
        int tw = Ui.width(label);
        g.text(Ui.font(), Ui.ui(label), x + (w - tw) / 2, y + (h - 8) / 2, this.active ? Ui.TEXT : Ui.TEXT_DIM, false);

        int knobColor = !this.active ? 0x5C6270 : hover ? 0xBBF7D0 : 0xF0FDF4;
        DivineRender.roundRect(g, kx, y + 2, kw, kh, kw / 2, DivineRender.argb(knobColor, a));

        if (this.isHovered) this.handleCursor(g);
        ci.cancel();
    }
}
