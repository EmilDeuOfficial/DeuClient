package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import eu.deusmp.deucore.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Bis 1.21.1 zeichnet Minecraft Bilder sofort und ohne Farbangabe. Diese Aufrufe bieten die Formen der neueren
 * Versionen an: Mischung einschalten, Farbe (samt Themefarbe) setzen, vorher Gesammeltes ausgeben.
 */
public final class Blit {
    private Blit() {}

    private static void begin(GuiGraphics graphics, int color) {
        graphics.flush(); // Flaechen davor muessen unter dem Bild liegen
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (color != -1) {
            int c = Theme.c(color);
            graphics.setColor(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
        }
    }

    private static void end(GuiGraphics graphics, int color) {
        if (color != -1) graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int tw, int th) {
        blit(graphics, texture, x, y, u, v, w, h, tw, th, -1);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int tw, int th,
                            int color) {
        if (color >>> 24 == 0) return;
        begin(graphics, color);
        graphics.blit(texture, x, y, u, v, w, h, tw, th);
        end(graphics, color);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int rw, int rh,
                            int tw, int th) {
        blit(graphics, texture, x, y, u, v, w, h, rw, rh, tw, th, -1);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int rw, int rh,
                            int tw, int th, int color) {
        if (color >>> 24 == 0) return;
        begin(graphics, color);
        graphics.blit(texture, x, y, w, h, u, v, rw, rh, tw, th);
        end(graphics, color);
    }

    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        sprite(graphics, sprite, x, y, w, h, -1);
    }

    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h, int color) {
        if (color >>> 24 == 0) return;
        begin(graphics, color);
        graphics.blitSprite(sprite, x, y, w, h);
        end(graphics, color);
    }

    public static void sprite(GuiGraphics graphics, TextureAtlasSprite sprite, int x, int y, int w, int h) {
        begin(graphics, -1);
        graphics.blit(x, y, 0, w, h, sprite);
        end(graphics, -1);
    }
}
