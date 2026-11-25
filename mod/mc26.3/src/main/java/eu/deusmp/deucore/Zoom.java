package eu.deusmp.deucore;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Zoom (Taste gedrückt halten, Standard: C) mit weichem Übergang. */
public final class Zoom {
    private Zoom() {}

    public static final float[] LEVELS = {2f, 4f, 6f, 10f};
    public static final String[] LEVEL_NAMES = {"2×", "4×", "6×", "10×"};

    private static float current = 1f;
    private static float previous = 1f;

    public static boolean isZooming() {
        Minecraft mc = Minecraft.getInstance();
        DeuConfig cfg = DeuConfig.get();
        return cfg.zoom && mc.player != null && mc.gui.screen() == null && InputConstants.isKeyDown(cfg.zoomKey);
    }

    /** jeden Client-Tick */
    public static void tick() {
        DeuConfig cfg = DeuConfig.get();
        float target = isZooming() ? LEVELS[Math.floorMod(cfg.zoomLevel, LEVELS.length)] : 1f;
        previous = current;
        current = cfg.zoomSmooth ? Mth.lerp(0.45f, current, target) : target;
        if (Math.abs(current - target) < 0.01f) current = target;
    }

    public static float currentFactor(float partialTick) {
        return Mth.lerp(partialTick, previous, current);
    }
}
