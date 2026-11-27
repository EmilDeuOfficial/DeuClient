package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.Theme;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Themefarbe fuer ersetzte Einzeltexturen (Trennlinien, Fortschritte-Fenster). */
@Mixin(TextureContents.class)
public abstract class ThemeTextureMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void deucore$theme(ResourceManager manager, Identifier id, CallbackInfoReturnable<TextureContents> cir) {
        if (Theme.ownTexture(id.getNamespace(), id.getPath())) Theme.recolor(cir.getReturnValue().image());
    }
}
