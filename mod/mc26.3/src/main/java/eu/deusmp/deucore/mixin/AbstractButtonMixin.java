package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.GoldButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeichnet den Hintergrund aller Standard-Buttons im Deu-Stil
 * (abgerundet, violetter Verlauf, goldener Rand beim Überfahren; GoldButton in Gold).
 * Die Beschriftung zeichnet Minecraft danach wie gewohnt.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget.WithInactiveMessage {
    public AbstractButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    /** Beschriftung aller Buttons in der glatten Deu-Schrift (Inhalt bleibt unverändert) */
    @Override
    public Component getMessage() {
        Component msg = super.getMessage();
        if (!DeuConfig.get().customButtons || !net.minecraft.network.chat.FontDescription.DEFAULT.equals(msg.getStyle().getFont())) return msg;
        return msg.copy().withStyle(st -> st.withFont(eu.deusmp.deucore.screen.Ui.uiFont()));
    }

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void deucore$sprite(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        int a = Mth.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isHoveredOrFocused();
        boolean gold = ((Object) this) instanceof GoldButton gb && gb.isGold();

        int r = Math.min(4, h / 4);
        if (gold) {
            DeuRender.roundGradient(g, x, y, w, h, r, DeuRender.argb(hover ? 0x6BFF9E : 0x2EFF7A, a), DeuRender.argb(hover ? 0x1FD35F : 0x17A84A, a));
        } else if (!this.active) {
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(0x070A08, Math.min(a, 170)));
        } else {
            if (hover) DeuRender.roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, DeuRender.argb(0x2EFF7A, a));
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(hover ? 0x141A16 : 0x0A0F0C, Math.min(a, hover ? 245 : 225)));
        }
        ci.cancel();
    }
}
