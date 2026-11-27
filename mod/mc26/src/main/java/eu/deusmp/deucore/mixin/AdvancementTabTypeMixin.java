package eu.deusmp.deucore.mixin;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reiter des Fortschritte-Fensters im Stil des Fensters: flach, abgerundet, duenner Rand.
 * Der gewaehlte Reiter geht ohne Trennlinie in das Fenster ueber.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.advancements.AdvancementTabType") // in aelteren Versionen nicht oeffentlich
public abstract class AdvancementTabTypeMixin {
    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow @Final private int max;

    @Inject(method = {"extractRenderState", "draw"}, at = @At("HEAD"), cancellable = true) // bis 1.21.11: draw
    private void deucore$tab(GuiGraphicsExtractor g, int x, int y, boolean selected, int index, CallbackInfo ci) {
        if (!DeuConfig.get().customButtons) return;
        ci.cancel();
        String type = ((Enum<?>) (Object) this).name();
        int panel = 0xFF121418;
        int line = selected ? 0x802EFF7A : 0x30FFFFFF;
        int fill = selected ? panel : 0xE60C0D10;
        int gap = selected ? 2 : 4; // Abstand an der Aussenseite: der gewaehlte Reiter ragt weiter heraus
        // Fensterkante liegt 4 Einheiten tief im Reiter; r* ist die ganze Box, c* der sichtbare Teil
        int rx = x, ry = y, rw = width, rh = height;
        int cx0 = x, cy0 = y, cx1 = x + width, cy1 = y + height;
        int join = selected ? 1 : 0; // gewaehlt: die Randlinie des Fensters wird ueberdeckt
        switch (type) {
            default -> { }
            case "ABOVE" -> { ry = y + gap; rh = 40; cy0 = ry; cy1 = y + height - 4 + join; }
            case "BELOW" -> { ry = y + height - gap - 40; rh = 40; cy0 = y + 4 - join; cy1 = y + height - gap; }
            case "LEFT" -> { rx = x + gap; rw = 40; cx0 = rx; cx1 = x + width - 4 + join; }
            case "RIGHT" -> { rx = x + width - gap - 40; rw = 40; cx0 = x + 4 - join; cx1 = x + width - gap; }
        }
        // am ersten und letzten Platz sitzt der Reiter auf einer runden Fensterecke: gerade weiterfuehren
        boolean first = index == 0, last = index == max - 1;
        if (selected && (first || last)) {
            int c = 6;
            if (type.equals("ABOVE") || type.equals("BELOW")) {
                int edge = type.equals("ABOVE") ? y + height - 4 : y + 4 - c;
                int side = first ? x : x + width - c;
                g.fill(side, edge, side + c, edge + c, panel);
                int lx = first ? x : x + width - 1;
                g.fill(lx, edge, lx + 1, edge + c, line);
            } else {
                int edge = type.equals("LEFT") ? x + width - 4 : x + 4 - c;
                int side = first ? y : y + height - c;
                g.fill(edge, side, edge + c, side + c, panel);
                int ly = first ? y : y + height - 1;
                g.fill(edge, ly, edge + c, ly + 1, line);
            }
        }
        g.enableScissor(cx0, cy0, cx1, cy1);
        DeuRender.roundRect(g, rx, ry, rw, rh, 5, fill);
        DeuRender.roundOutline(g, rx, ry, rw, rh, 5, line);
        g.disableScissor();
    }
}
