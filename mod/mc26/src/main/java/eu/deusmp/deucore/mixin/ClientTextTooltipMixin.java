package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Text der Info-Boxen in der DeuClient-Schrift (Farben und Sonderschriften bleiben). */
@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipMixin {
    @Shadow @Final @Mutable
    private FormattedCharSequence text;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void deucore$font(FormattedCharSequence original, CallbackInfo ci) {
        if (DeuConfig.get().customButtons) this.text = Ui.themedFont(this.text);
    }
}
