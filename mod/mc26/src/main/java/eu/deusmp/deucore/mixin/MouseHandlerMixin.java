package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Beim Zoomen: das Mausrad veraendert die Zoomstufe statt die Schnellleiste, die Maus dreht entsprechend feiner. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void deucore$zoomScroll(long window, double x, double y, CallbackInfo ci) {
        if (Zoom.scroll(y)) ci.cancel();
    }

    @ModifyArg(method = "turnPlayer", index = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private double deucore$zoomTurnX(double x) {
        return x * Zoom.turnFactor();
    }

    @ModifyArg(method = "turnPlayer", index = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private double deucore$zoomTurnY(double y) {
        return y * Zoom.turnFactor();
    }
}
