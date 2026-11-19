package eu.divinesmp.divinecore.cosmetics;

/** Zusatzdaten am AvatarRenderState (per Mixin): welche Flügel der Spieler trägt. */
public interface DivineAvatarState {
    String divinecore$getWings();

    void divinecore$setWings(String wings);
}
