package eu.deusmp.deucore.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Hervorgehobener Button (gold statt violett). Das Aussehen setzt AbstractButtonMixin;
 * "selected" markiert z. B. die aktive Kategorie im Deu-Menü.
 */
public class GoldButton extends Button {
    private final boolean gold;
    private boolean selected;

    public GoldButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        this(x, y, w, h, message, onPress, true);
    }

    public GoldButton(int x, int y, int w, int h, Component message, OnPress onPress, boolean gold) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.gold = gold;
    }

    public boolean isGold() {
        return gold || selected;
    }

    public GoldButton selected(boolean s) {
        this.selected = s;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        this.extractDefaultSprite(g);
        this.extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}
