package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/* Ueberschrift ueber dem Fenster: siehe AdvancementsScreenMixin */

/** Info-Box im Fortschritte-Fenster: Text in der DeuClient-Schrift. */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {
    @ModifyArg(method = "extractMultilineText", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
    private FormattedCharSequence deucore$lines(FormattedCharSequence line) {
        return DeuConfig.get().customButtons ? Ui.themedFont(line) : line;
    }

    @ModifyArg(method = "extractHover", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private Component deucore$progress(Component text) {
        return DeuConfig.get().customButtons ? text.copy().withStyle(st -> st.withFont(Ui.uiFont())) : text;
    }
}
