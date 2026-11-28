package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.screen.Ui;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.8: Chunk-Karte und Prozentzahl beim Laden einer Welt im DeuClient-Stil. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Shadow @Final private static Object2IntMap<ChunkStatus> COLORS;

    @Inject(method = "renderChunks", at = @At("HEAD"), cancellable = true)
    private static void deucore$map(GuiGraphics g, StoringChunkProgressListener view, int cx, int cy, int cell, int spacing, CallbackInfo ci) {
        if (!DeuConfig.get().customLoadingScreen) return;
        int stride = cell + spacing;
        int n = view.getDiameter();
        int side = n * stride - spacing;
        int x0 = cx - side / 2, y0 = cy - side / 2;
        DeuRender.roundRect(g, x0 - 7, y0 - 7, side + 14, side + 14, 6, 0xE6121418);
        DeuRender.roundOutline(g, x0 - 7, y0 - 7, side + 14, side + 14, 6, 0x502EFF7A);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                ChunkStatus status = view.getStatus(i, j);
                int c = COLORS.getInt(status);
                float l = (((c >> 16) & 255) * 0.3f + ((c >> 8) & 255) * 0.59f + (c & 255) * 0.11f) / 255f;
                float t = Math.max(0f, Math.min(1f, (l - 0.15f) / 0.7f));
                int color = DeuRender.lerpArgb(0xFF1C1F24, 0xFF2EFF7A, t);
                DeuRender.roundRect(g, x0 + i * stride, y0 + j * stride, cell, cell, 1, color);
            }
        }
        ci.cancel();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void deucore$text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (!DeuConfig.get().customLoadingScreen) {
            g.drawCenteredString(font, text, x, y, color);
            return;
        }
        Ui.centered(g, text.getString(), x, y, Ui.TEXT);
    }
}
