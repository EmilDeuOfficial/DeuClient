package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineRender;
import eu.divinesmp.divinecore.screen.GoldButtonWidget;
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
 * Zeichnet alle normalen Minecraft-Buttons im Divine-Stil:
 * abgerundet, violetter Verlauf, goldener Rand beim Überfahren.
 * Gold-Buttons (GoldButtonWidget) sind zusätzlich golden gefüllt.
 */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget {

    public PressableWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void divinecore$renderDivineButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int a = MathHelper.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isSelected();
        boolean gold = ((Object) this) instanceof GoldButtonWidget;

        int border, top, bottom, text;
        if (!this.active) {
            border = 0x1E3326; top = 0x142019; bottom = 0x0C140F; text = 0x7C9A87;
        } else if (gold) {
            border = hover ? 0xBBF7D0 : 0x22C55E;
            top = hover ? 0x86EFAC : 0x22C55E;
            bottom = hover ? 0x15803D : 0x14532D;
            text = 0x04140A;
        } else {
            border = hover ? 0x22C55E : 0x2E7D4F;
            top = hover ? 0x16A34A : 0x14532D;
            bottom = hover ? 0x14532D : 0x0A2414;
            text = hover ? 0x86EFAC : 0xFFFFFF;
        }

        // Leuchten beim Überfahren
        if (hover) DivineRender.roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, DivineRender.argb(gold ? 0xBBF7D0 : 0x22C55E, a / 4));
        DivineRender.roundedRect(ctx, x, y, w, h, DivineRender.argb(border, a));
        DivineRender.roundedGradient(ctx, x + 1, y + 1, w - 2, h - 2, DivineRender.argb(top, a), DivineRender.argb(bottom, a));
        // feiner Glanz oben
        ctx.fill(x + 3, y + 1, x + w - 3, y + 2, DivineRender.argb(0xFFFFFF, a / (hover ? 3 : 6)));

        PressableWidget self = (PressableWidget) (Object) this;
        if (gold) {
            // dunkle Schrift ohne Schatten auf Gold
            Text msg = getMessage();
            int tx = x + (w - MinecraftClient.getInstance().textRenderer.getWidth(msg)) / 2;
            ctx.drawText(MinecraftClient.getInstance().textRenderer, msg, tx, y + (h - 8) / 2, DivineRender.argb(text, a), false);
        } else {
            self.drawMessage(ctx, MinecraftClient.getInstance().textRenderer, DivineRender.argb(text, a));
        }
        ci.cancel();
    }
}
