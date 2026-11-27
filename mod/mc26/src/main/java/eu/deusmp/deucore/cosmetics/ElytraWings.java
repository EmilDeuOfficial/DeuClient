package eu.deusmp.deucore.cosmetics;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluegel anstelle der Elytra: waehlt aus der Bewegung des Spielers die passende Blockbench-Animation
 * und blendet weich zwischen den Posen.
 */
public final class ElytraWings {
    private ElytraWings() {}

    /** Namen der Animationen in den .bbmodel-Dateien (siehe tools/wings/animations.cjs); Index = Pose */
    public static final String[] CLIPS = {"stehen", "schleichen", "gleiten", "schlagen", "sturz"};
    private static final int STAND = 0, SNEAK = 1, GLIDE = 2, FLAP = 3, DIVE = 4;
    /** Geschwindigkeit des Ueberblendens (je Sekunde) */
    private static final float BLEND = 7f;

    private static final class Blend {
        final float[] weights = new float[CLIPS.length];
        long last;
    }

    private static final Map<Integer, Blend> BLENDS = new HashMap<>();

    /** Nur fuer Testbilder (DemoMode): erzwingt eine Pose, -1 = aus */
    public static int forced = -1;

    public static int pose(Avatar avatar) {
        if (forced >= 0) return forced;
        if (!avatar.isFallFlying()) return avatar.isCrouching() ? SNEAK : STAND;
        Vec3 v = avatar.getDeltaMovement();
        double speed = v.length();
        if (v.y > 0.08 || speed > 1.25) return FLAP; // Steigflug oder Raketenschub
        if (speed > 0.1 && v.y / speed < -0.6) return DIVE;
        return GLIDE;
    }

    /** Gewichte aller Animationen fuer diesen Spieler; naehern sich der Zielpose an (nur vom Render-Thread) */
    static float[] weights(int entityId, int target) {
        long now = System.nanoTime();
        Blend b = BLENDS.get(entityId);
        if (b == null || now - b.last > 1_000_000_000L) {
            if (BLENDS.size() > 256) BLENDS.clear();
            b = new Blend();
            b.weights[target] = 1f;
            BLENDS.put(entityId, b);
        } else {
            float k = 1f - (float) Math.exp(-(now - b.last) / 1e9f * BLEND);
            float sum = 0f;
            for (int i = 0; i < CLIPS.length; i++) {
                b.weights[i] += ((i == target ? 1f : 0f) - b.weights[i]) * k;
                sum += b.weights[i];
            }
            for (int i = 0; i < CLIPS.length; i++) b.weights[i] /= sum;
        }
        b.last = now;
        return b.weights.clone();
    }
}
