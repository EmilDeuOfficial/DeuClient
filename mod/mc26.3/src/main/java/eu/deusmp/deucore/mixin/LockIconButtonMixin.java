package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.LockIconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Schloss-Button (Schwierigkeit sperren) im DeuClient-Look: dunkles abgerundetes Feld mit Linien-Icon. */
@Mixin(LockIconButton.class)
public abstract class LockIconButtonMixin {
    @Shadow private boolean locked;

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void deucore$draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        AbstractWidget w = (AbstractWidget) (Object) this;
        boolean hover = w.active && w.isHoveredOrFocused();
        // 3 px Abstand zum Button links daneben (Vanilla setzt beide ohne Luecke nebeneinander)
        int x = w.getX() + 3, y = w.getY(), bw = w.getWidth() - 3, bh = w.getHeight();
        if (hover) DeuRender.roundRect(g, x - 1, y - 1, bw + 2, bh + 2, 4, 0xFF2EFF7A);
        DeuRender.roundRect(g, x, y, bw, bh, 3, hover ? 0xF5141A16 : 0xE10A0F0C);
        int color = !w.active ? 0xFF5C6270 : this.locked ? 0xFF2EFF7A : 0xFFF3F4F7;
        Ui.iconCentered(g, this.locked ? Ui.I_LOCK : Ui.I_UNLOCK, x + bw / 2, y + bh / 2, Math.min(bw, bh) - 8, color);
        ci.cancel();
    }
}
