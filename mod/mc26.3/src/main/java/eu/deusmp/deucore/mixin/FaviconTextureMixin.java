package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.DeuConfig;
import net.minecraft.client.gui.screens.FaviconTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Welt- und Server-Icons mit abgerundeten Ecken (Alpha-Maske mit 4x4-Supersampling, glatte Kanten). */
@Mixin(FaviconTexture.class)
public abstract class FaviconTextureMixin {
    @ModifyVariable(method = "upload", at = @At("HEAD"), argsOnly = true)
    private NativeImage deucore$round(NativeImage image) {
        if (!DeuConfig.get().customButtons || image == null) return image;
        int w = image.getWidth(), h = image.getHeight();
        double r = Math.min(w, h) * 0.2; // Eckenradius = 20 % der Kantenlaenge
        int ri = (int) Math.ceil(r);
        for (int cy = 0; cy < ri; cy++) {
            for (int cx = 0; cx < ri; cx++) {
                int hit = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        double dx = cx + (sx + 0.5) / 4.0 - r, dy = cy + (sy + 0.5) / 4.0 - r;
                        if (dx * dx + dy * dy <= r * r) hit++;
                    }
                }
                if (hit == 16) continue;
                // gleicher Faktor fuer alle vier Ecken
                int[][] pts = {{cx, cy}, {w - 1 - cx, cy}, {cx, h - 1 - cy}, {w - 1 - cx, h - 1 - cy}};
                for (int[] p : pts) {
                    int c = image.getPixel(p[0], p[1]);
                    int a = (c >>> 24) & 255;
                    int na = a * hit / 16;
                    image.setPixel(p[0], p[1], (na << 24) | (c & 0xFFFFFF));
                }
            }
        }
        return image;
    }
}
