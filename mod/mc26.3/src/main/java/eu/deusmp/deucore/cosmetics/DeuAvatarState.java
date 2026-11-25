package eu.deusmp.deucore.cosmetics;

/** Zusatzdaten am AvatarRenderState (per Mixin): welche Flügel der Spieler trägt. */
public interface DeuAvatarState {
    String deucore$getWings();

    void deucore$setWings(String wings);
}
