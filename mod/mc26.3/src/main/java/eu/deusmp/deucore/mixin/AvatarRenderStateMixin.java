package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.cosmetics.DeuAvatarState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Speichert pro gerendertem Spieler, welche Deu-Flügel er trägt. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements DeuAvatarState {
    @Unique
    private String deucore$wings = "";

    @Override
    public String deucore$getWings() {
        return deucore$wings;
    }

    @Override
    public void deucore$setWings(String wings) {
        this.deucore$wings = wings;
    }
}
