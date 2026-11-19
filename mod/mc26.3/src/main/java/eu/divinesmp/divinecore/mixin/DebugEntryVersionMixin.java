package eu.divinesmp.divinecore.mixin;

import eu.divinesmp.divinecore.DivineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryVersion;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F3-Bildschirm: Divine-Zeile mit Spielername direkt unter der Minecraft-Version. */
@Mixin(DebugEntryVersion.class)
public abstract class DebugEntryVersionMixin {
    @Inject(method = "display", at = @At("TAIL"))
    private void divinecore$name(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk, CallbackInfo ci) {
        if (!DivineConfig.get().f3Name) return;
        String name = Minecraft.getInstance().getUser().getName();
        displayer.addPriorityLine("§a✦ DeuClient §7| §fSpieler: §e" + name);
    }
}
