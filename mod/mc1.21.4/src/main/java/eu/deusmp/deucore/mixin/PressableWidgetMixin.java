package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.GoldButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeichnet alle normalen Minecraft-Buttons im Deu-Stil:
 * abgerundet, violetter Verlauf, goldener Rand beim Überfahren.
 * Gold-Buttons (GoldButtonWidget) sind zusätzlich golden gefüllt.
 */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget {

    public PressableWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void deucore$renderDeuButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int a = MathHelper.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isSelected();
        boolean gold = ((Object) this) instanceof GoldButtonWidget;

        int border, top, bottom, text;
        if (!this.active) {
            border = 0x1E3324; top = 0x142018; bottom = 0x0C1410; text = 0x7C9A86;
        } else if (gold) {
            border = hover ? 0xD4FFE4 : 0x2EFF7A;
            top = hover ? 0x9FFFC0 : 0x2EFF7A;
            bottom = hover ? 0x17A84A : 0x0F7A35;
            text = 0x04140A;
        } else {
            border = hover ? 0x2EFF7A : 0x2E8F4A;
            top = hover ? 0x1FD35F : 0x0F7A35;
            bottom = hover ? 0x0F7A35 : 0x0A2814;
            text = hover ? 0x9FFFC0 : 0xFFFFFF;
        }

        // Leuchten beim Überfahren
        if (hover) DeuRender.roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, DeuRender.argb(gold ? 0xD4FFE4 : 0x2EFF7A, a / 4));
        DeuRender.roundedRect(ctx, x, y, w, h, DeuRender.argb(border, a));
        DeuRender.roundedGradient(ctx, x + 1, y + 1, w - 2, h - 2, DeuRender.argb(top, a), DeuRender.argb(bottom, a));
        // feiner Glanz oben
        ctx.fill(x + 3, y + 1, x + w - 3, y + 2, DeuRender.argb(0xFFFFFF, a / (hover ? 3 : 6)));

        PressableWidget self = (PressableWidget) (Object) this;
        if (gold) {
            // dunkle Schrift ohne Schatten auf Gold
            Text msg = getMessage();
            int tx = x + (w - MinecraftClient.getInstance().textRenderer.getWidth(msg)) / 2;
            ctx.drawText(MinecraftClient.getInstance().textRenderer, msg, tx, y + (h - 8) / 2, DeuRender.argb(text, a), false);
        } else {
            self.drawMessage(ctx, MinecraftClient.getInstance().textRenderer, DeuRender.argb(text, a));
        }
        ci.cancel();
    }
}
