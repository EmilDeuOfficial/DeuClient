package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.Theme;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Closeable;
import java.lang.reflect.Method;

/** Bis 1.21.3: Themefarbe fuer ersetzte Oberflaechen-Texturen (die Bild-Klasse ist nicht oeffentlich). */
@Mixin(targets = "net.minecraft.client.renderer.texture.SimpleTexture$TextureImage")
public abstract class ThemeTextureMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void deucore$theme(ResourceManager manager, ResourceLocation id, CallbackInfoReturnable<Closeable> cir) {
        if (!Theme.ownTexture(id.getNamespace(), id.getPath())) return;
        Object loaded = cir.getReturnValue();
        try {
            // der Methodenname ist im fertigen Spiel verschleiert: nach dem Ergebnistyp suchen
            for (Method method : loaded.getClass().getDeclaredMethods()) {
                if (method.getReturnType() == NativeImage.class && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    Theme.recolor((NativeImage) method.invoke(loaded));
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            // Bild liess sich nicht laden: unveraendert lassen
        }
    }
}
