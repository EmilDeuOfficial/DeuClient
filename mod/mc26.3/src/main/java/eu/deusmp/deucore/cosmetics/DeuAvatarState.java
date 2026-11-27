package eu.deusmp.deucore.cosmetics;

/** Zusatzdaten am AvatarRenderState (per Mixin): welche Flügel der Spieler trägt (und ob sie die Elytra ersetzen). */
public interface DeuAvatarState {
    String deucore$getWings();

    void deucore$setWings(String wings);

    /** Pose der Fluegel, die die Elytra ersetzen (siehe {@link ElytraWings}); -1 = normale Elytra */
    int deucore$getElytraPose();

    void deucore$setElytraPose(int pose);
}
