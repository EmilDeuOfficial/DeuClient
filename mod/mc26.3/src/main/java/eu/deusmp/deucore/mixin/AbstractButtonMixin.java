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
 * (abgerundet, flach und neutral, beim Überfahren heller; GoldButton in Akzentfarbe).
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
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(hover ? 0x3CEB7E : 0x27D86A, a));
        } else if (!this.active) {
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(0x0C0D10, Math.min(a, 170)));
        } else {
            DeuRender.roundRect(g, x, y, w, h, r, DeuRender.argb(hover ? 0x262A31 : 0x16181D, Math.min(a, hover ? 245 : 230)));
        }
        ci.cancel();
    }
}
