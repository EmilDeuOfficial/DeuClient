package eu.deusmp.deucore.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.Theme;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;

/** Themefarbe fuer das Icon neben den Namen (Bild-Schrift deucore:font/logo.png). */
@Mixin(targets = "net.minecraft.client.gui.font.providers.BitmapProvider$Definition")
public abstract class ThemeNameIconMixin {
    @Shadow @Final private Identifier file;

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private NativeImage deucore$theme(InputStream in) throws IOException {
        NativeImage img = NativeImage.read(in);
        if (file.getNamespace().equals("deucore") && file.getPath().equals("font/logo.png")) Theme.recolor(img);
        return img;
    }
}
