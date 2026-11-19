package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.cosmetics.DivineAvatarState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Speichert pro gerendertem Spieler, welche Divine-Flügel er trägt. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements DivineAvatarState {
    @Unique
    private String divinecore$wings = "";

    @Override
    public String divinecore$getWings() {
        return divinecore$wings;
    }

    @Override
    public void divinecore$setWings(String wings) {
        this.divinecore$wings = wings;
    }
}
