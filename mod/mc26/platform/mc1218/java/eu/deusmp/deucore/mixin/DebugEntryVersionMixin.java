package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Bis 1.21.8 baut der F3-Bildschirm seine Zeilen selbst: eigene Zeile direkt unter der Versionszeile. */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugEntryVersionMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void deucore$name(CallbackInfoReturnable<List<String>> cir) {
        if (!DeuConfig.get().f3Name) return;
        String name = Minecraft.getInstance().getUser().getName();
        List<String> lines = cir.getReturnValue();
        //  = Namensschild-Icon in der Standardschrift (reiner Text, so zeigen es auch andere F3-Mods)
        lines.add(Math.min(1, lines.size()), "§f §aDeuClient §7| §f" + Tr.t("Spieler") + ": §e" + name);
    }
}
