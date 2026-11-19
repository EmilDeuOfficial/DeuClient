package eu.divinesmp.divinecore.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Hervorgehobener Button (gold statt violett).
 * Die eigentliche Darstellung übernimmt PressableWidgetMixin.
 */
public class GoldButtonWidget extends ButtonWidget {
    public GoldButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }
}
