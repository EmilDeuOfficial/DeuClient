package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.Theme;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Themefarbe fuer ersetzte Oberflaechen-Sprites (Reiter, Scrollbalken, Rahmen). */
@Mixin(SpriteContents.class)
public abstract class ThemeSpriteMixin {
    @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;"
            + "Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("RETURN"))
    private void deucore$theme(Identifier name, net.minecraft.client.resources.metadata.animation.FrameSize size, NativeImage image,
                               java.util.Optional<?> animation, java.util.List<?> metadata, java.util.Optional<?> texture, CallbackInfo ci) {
        if (Theme.ownTexture(name.getNamespace(), "textures/gui/sprites/" + name.getPath() + ".png")) Theme.recolor(image);
    }
}
