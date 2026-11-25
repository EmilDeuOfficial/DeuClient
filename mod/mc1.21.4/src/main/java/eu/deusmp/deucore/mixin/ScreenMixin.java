package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt den Hintergrund von Menüs:
 *  - außerhalb einer Welt (Hauptmenü, Multiplayer, Optionen, Verbinden …): animierter Deu-Hintergrund
 *  - Pause-Menü: violetter Deu-Overlay mit Schriftzug
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow protected MinecraftClient client;
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void deucore$background(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.client == null) return;
        if (this.client.world == null) {
            DeuRender.menuBackground(ctx, this.width, this.height, 255);
            ci.cancel();
        } else if (((Object) this) instanceof GameMenuScreen) {
            DeuRender.pauseOverlay(ctx, this.width, this.height);
            int lw = Math.min(150, this.width / 3);
            DeuRender.drawWordmark(ctx, this.width / 2 - lw / 2, 4, lw, 230);
            ci.cancel();
        }
    }
}
