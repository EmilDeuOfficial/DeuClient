package eu.deusmp.deucore;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Zoom (Taste gedrückt halten, Standard: C) mit weichem Übergang; das Mausrad zoomt weiter hinein oder heraus. */
public final class Zoom {
    private Zoom() {}

    public static final float[] LEVELS = {2f, 4f, 6f, 10f};
    public static final String[] LEVEL_NAMES = {"2×", "4×", "6×", "10×"};

    private static float current = 1f;
    private static float previous = 1f;
    /** Faktor aus dem Mausrad, gilt bis die Zoom-Taste losgelassen wird */
    private static float wheel = 1f;
    private static final float MIN = 1.5f, MAX = 50f, STEP = 1.25f;

    private static float level() {
        return LEVELS[Math.floorMod(DeuConfig.get().zoomLevel, LEVELS.length)];
    }

    /** Mausrad: true = beim Zoomen verbraucht (die Schnellleiste bleibt dann stehen) */
    public static boolean scroll(double amount) {
        if (amount == 0 || !isZooming()) return false;
        float target = Mth.clamp(level() * wheel * (amount > 0 ? STEP : 1f / STEP), MIN, MAX);
        wheel = target / level();
        return true;
    }

    public static boolean isZooming() {
        Minecraft mc = Minecraft.getInstance();
        DeuConfig cfg = DeuConfig.get();
        return cfg.zoom && mc.player != null && mc.gui.screen() == null && eu.deusmp.deucore.compat.Platform.keyDown(cfg.zoomKey);
    }

    /** jeden Client-Tick */
    public static void tick() {
        DeuConfig cfg = DeuConfig.get();
        boolean zooming = isZooming();
        if (!zooming) wheel = 1f;
        float target = zooming ? level() * wheel : 1f;
        previous = current;
        current = cfg.zoomSmooth ? Mth.lerp(0.45f, current, target) : target;
        if (Math.abs(current - target) < 0.01f) current = target;
    }

    /** Faktor fuer die Mausbewegung: beim Zoomen so viel langsamer, wie das Bild vergroessert ist */
    public static double turnFactor() {
        float zoom = currentFactor(1f);
        if (zoom <= 1.001f) return 1;
        double half = Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2;
        return Math.tan(half / zoom) / Math.tan(half);
    }

    public static float currentFactor(float partialTick) {
        return Mth.lerp(partialTick, previous, current);
    }
}
