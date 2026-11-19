package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineRender;
import eu.divinesmp.divinecore.screen.GoldButton;
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
 * Zeichnet den Hintergrund aller Standard-Buttons im Divine-Stil
 * (abgerundet, violetter Verlauf, goldener Rand beim Überfahren; GoldButton in Gold).
 * Die Beschriftung zeichnet Minecraft danach wie gewohnt.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget.WithInactiveMessage {
    public AbstractButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    /** Beschriftung aller Buttons in der glatten Divine-Schrift (Inhalt bleibt unverändert) */
    @Override
    public Component getMessage() {
        Component msg = super.getMessage();
        if (!DivineConfig.get().customButtons || !net.minecraft.network.chat.FontDescription.DEFAULT.equals(msg.getStyle().getFont())) return msg;
        return msg.copy().withStyle(st -> st.withFont(UI_FONT));
    }

    private static final net.minecraft.network.chat.FontDescription UI_FONT =
            new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("divinecore", "ui"));

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void divinecore$sprite(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!DivineConfig.get().customButtons) return;
        int a = Mth.ceil(this.alpha * 255f);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = this.active && this.isHoveredOrFocused();
        boolean gold = ((Object) this) instanceof GoldButton gb && gb.isGold();

        int r = Math.min(4, h / 4);
        if (gold) {
            DivineRender.roundGradient(g, x, y, w, h, r, DivineRender.argb(hover ? 0x4ADE80 : 0x22C55E, a), DivineRender.argb(hover ? 0x16A34A : 0x15803D, a));
        } else if (!this.active) {
            DivineRender.roundRect(g, x, y, w, h, r, DivineRender.argb(0x070A08, Math.min(a, 170)));
        } else {
            if (hover) DivineRender.roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, DivineRender.argb(0x22C55E, a));
            DivineRender.roundRect(g, x, y, w, h, r, DivineRender.argb(hover ? 0x141A16 : 0x0A0F0C, Math.min(a, hover ? 245 : 225)));
        }
        ci.cancel();
    }
}
