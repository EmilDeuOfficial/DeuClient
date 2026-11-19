package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeichnet alle Schieberegler im DeuClient-Stil: dunkle Schiene, gruene Fuellung bis zum
 * schmalen runden Griff, weich abgerundete (kantengeglaettete) Ecken.
 */
@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin extends ClickableWidget {
    @Shadow protected double value;

    public SliderWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void divinecore$slider(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int a = MathHelper.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isSelected();
        int r = Math.min(4, h / 4);

        if (hover) DivineRender.roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, DivineRender.argb(0x22C55E, a));
        DivineRender.roundedRect(ctx, x, y, w, h, r, DivineRender.argb(this.active ? 0x0A0F0C : 0x070A08, Math.min(a, 235)));

        int kw = 7, kh = h - 4;
        int travel = Math.max(0, w - 4 - kw);
        int kx = x + 2 + (int) Math.round(MathHelper.clamp(this.value, 0.0, 1.0) * travel);
        if (this.active) {
            DivineRender.roundedGradient(ctx, x + 2, y + 2, kx + kw - (x + 2), kh, Math.min(4, kh / 4), DivineRender.argb(0x16A34A, a), DivineRender.argb(0x14532D, a));
        }
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), x + w / 2, y + (h - 8) / 2,
                DivineRender.argb(this.active ? 0xFFFFFF : 0x7C9A87, a));
        int knobColor = !this.active ? 0x5C6270 : hover ? 0xBBF7D0 : 0xF0FDF4;
        DivineRender.roundedRect(ctx, kx, y + 2, kw, kh, kw / 2, DivineRender.argb(knobColor, a));
        ci.cancel();
    }
}
