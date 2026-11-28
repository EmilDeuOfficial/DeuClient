package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.platform.NativeImage;

/** Bis 1.21.1 stehen Bildpunkte als ABGR im Speicher: hier in die uebliche Reihenfolge ARGB umrechnen. */
public final class Pixels {
    private Pixels() {}

    private static int swap(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }

    public static int get(NativeImage image, int x, int y) {
        return swap(image.getPixelRGBA(x, y));
    }

    public static void set(NativeImage image, int x, int y, int argb) {
        image.setPixelRGBA(x, y, swap(argb));
    }
}
