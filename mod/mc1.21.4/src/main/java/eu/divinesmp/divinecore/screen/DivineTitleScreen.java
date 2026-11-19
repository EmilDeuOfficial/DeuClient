package eu.divinesmp.divinecore.screen;

import eu.divinesmp.divinecore.DivineCore;
import eu.divinesmp.divinecore.DivineRender;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Eigenes Divine-Hauptmenü (ersetzt das Minecraft-Titelmenü):
 * Divine-Schriftzug statt Minecraft-Logo, animierter Hintergrund, abgerundete Buttons
 * mit Einzelspieler, Mehrspieler, Optionen und Beenden.
 */
public class DivineTitleScreen extends Screen {
    private static final int BUTTON_W = 220;
    private static final int BUTTON_H = 22;
    private static final int GAP = 6;
    private final long openedAt = Util.getMeasuringTimeMs();

    public DivineTitleScreen() {
        super(Text.literal("DeuClient"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BUTTON_W / 2;
        int y = Math.max(this.height / 2 - 18, logoBottom() + 14);

        // 1. Einzelspieler / Mehrspieler
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("menu.singleplayer"), b -> this.client.setScreen(new SelectWorldScreen(this)))
                .dimensions(x, y, BUTTON_W, BUTTON_H).build());
        y += BUTTON_H + GAP;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("menu.multiplayer"), b -> this.client.setScreen(new MultiplayerScreen(this)))
                .dimensions(x, y, BUTTON_W, BUTTON_H).build());
        y += BUTTON_H + GAP;

        // 2. Optionen | Beenden
        int half = (BUTTON_W - GAP) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("menu.options"), b -> this.client.setScreen(new OptionsScreen(this, this.client.options)))
                .dimensions(x, y, half, BUTTON_H).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("menu.quit"), b -> this.client.scheduleStop())
                .dimensions(x + half + GAP, y, half, BUTTON_H).build());
    }

    private int logoWidth() {
        return Math.min(300, (int) (this.width * 0.62f));
    }

    private int logoTop() {
        return Math.max(12, this.height / 2 - 130);
    }

    private int logoBottom() {
        return logoTop() + Math.round(logoWidth() * DivineRender.WORDMARK_H / (float) DivineRender.WORDMARK_W);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // Hintergrund (ScreenMixin) + Buttons

        // Schriftzug mit sanftem Einblenden und leichtem Schweben
        float age = (Util.getMeasuringTimeMs() - openedAt) / 1000f;
        int alpha = (int) (255 * MathHelper.clamp(age * 1.6f, 0f, 1f));
        int lw = logoWidth();
        int float_ = Math.round(MathHelper.sin(age * 1.4f) * 3f);
        DivineRender.drawWordmark(context, this.width / 2 - lw / 2, logoTop() + float_, lw, alpha);

        // Fußzeile
        String version = "DeuClient · Minecraft " + SharedConstants.getGameVersion().getName();
        context.drawTextWithShadow(this.textRenderer, version, 4, this.height - 12, DivineRender.argb(0xA3BFAE, 200));
        String copyright = "Copyright Mojang AB. Do not distribute!";
        context.drawTextWithShadow(this.textRenderer, copyright, this.width - this.textRenderer.getWidth(copyright) - 4, this.height - 12, DivineRender.argb(0xFFFFFF, 140));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
