package eu.divinesmp.divinecore.screen;

import com.mojang.blaze3d.platform.NativeImage;
import eu.divinesmp.divinecore.DivineCore;
import eu.divinesmp.divinecore.DivineRender;
import eu.divinesmp.divinecore.cosmetics.Cosmetics;
import eu.divinesmp.divinecore.screen.Ui.IconButton;
import eu.divinesmp.divinecore.screen.Ui.IconButton.Style;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Divine-Hauptmenü im cleanen Client-Look:
 *  - Mitte: Logo "DIVINE CLIENT", Haupt-Buttons mit Icons, "Spiel beenden"
 *  - oben rechts: Profil (Kopf + Name) und quadratische Schnellzugriffe
 *  - links: Server-Leiste mit den Servern aus der Multiplayer-Liste (Klick = verbinden)
 *  - unten rechts: Kosmetik-Karte
 */
public class DivineTitleScreen extends Screen {
    private static final int BW = 200;
    private static Supplier<PlayerSkin> skinLookup;
    private final long openedAt = System.currentTimeMillis();
    private int bh = 22, gap = 6;
    private final List<DockEntry> dock = new ArrayList<>();

    private record DockEntry(ServerData data, Identifier icon) {}

    public DivineTitleScreen() {
        super(Component.literal("DeuClient"));
    }

    private int logoY() {
        int needed = 32 + 16 + 5 * (bh + gap) + 2 + 20 + 22 + 20;
        return Math.max(40, Math.min(this.height / 2 - 112, this.height - needed));
    }

    @Override
    protected void init() {
        bh = this.height < 300 ? 20 : 22;
        gap = this.height < 300 ? 4 : 6;
        int cx = this.width / 2;
        int x = cx - BW / 2;
        int y = logoY() + 48;

        addRenderableWidget(new IconButton(x, y, BW, bh, Ui.I_PERSON, Component.translatable("menu.singleplayer"),
                b -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
        y += bh + gap;
        addRenderableWidget(new IconButton(x, y, BW, bh, Ui.I_PEOPLE, Component.translatable("menu.multiplayer"),
                b -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
        y += bh + gap;
        addRenderableWidget(new IconButton(x, y, BW, bh, Ui.I_WINGS, Component.literal("Kosmetik"),
                b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(this, DivineModMenuScreen.Tab.COSMETICS))));
        y += bh + gap;
        addRenderableWidget(new IconButton(x, y, BW, bh, Ui.I_GRID, Component.literal("Mod-Menü"),
                b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(this))));
        y += bh + gap;
        y += 12;

        int qy = Math.min(y, this.height - 42);
        addRenderableWidget(new IconButton(cx - 60, qy, 120, 20, null, Component.literal("SPIEL BEENDEN"), Style.DANGER, 0, b -> this.minecraft.stop()));

        // Schnellzugriff oben rechts (quadratisch)
        int s = 24, qx = this.width - 8 - s;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_LANGUAGE, Component.empty(), b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager()))));
        qx -= s + 5;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_GEAR, Component.empty(), b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));
        qx -= s + 5;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_WINGS, Component.empty(), b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(this, DivineModMenuScreen.Tab.COSMETICS))));
        qx -= s + 5;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_GRID, Component.empty(), b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(this))));

        // Server-Leiste links
        loadDock();
        int dy = dockTop();
        for (DockEntry e : dock) {
            addRenderableWidget(new IconButton(6, dy, 28, 28, null, Component.empty(), Style.GHOST, 0, b -> join(e.data())));
            dy += 32;
        }

        if (showPromo()) {
            addRenderableWidget(new IconButton(this.width - 186, this.height - 96, 176, 84, null, Component.empty(), Style.GHOST, 0,
                    b -> this.minecraft.gui.setScreen(new DivineModMenuScreen(this, DivineModMenuScreen.Tab.COSMETICS))));
        }
    }

    /* ---------------- Server-Leiste ---------------- */

    private void loadDock() {
        dock.clear();
        try {
            ServerList list = new ServerList(this.minecraft);
            list.load();
            int max = Math.min(list.size(), Math.max(1, (this.height - 80) / 32));
            for (int i = 0; i < Math.min(max, 6); i++) {
                ServerData sd = list.get(i);
                Identifier icon = null;
                byte[] bytes = sd.getIconBytes();
                if (bytes != null) {
                    try {
                        icon = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/server/" + i);
                        this.minecraft.getTextureManager().register(icon, new DynamicTexture(icon::toString, NativeImage.read(bytes)));
                    } catch (Exception ex) {
                        icon = null;
                    }
                }
                dock.add(new DockEntry(sd, icon));
            }
        } catch (Exception e) {
            DivineCore.LOGGER.debug("Serverliste nicht lesbar", e);
        }
    }

    private int dockTop() {
        return this.height / 2 - dock.size() * 32 / 2;
    }

    private void join(ServerData data) {
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    private boolean showPromo() {
        return this.width / 2 + BW / 2 + 16 < this.width - 186 && this.height > 230;
    }

    private PlayerSkin skin() {
        if (skinLookup == null) skinLookup = Minecraft.getInstance().getSkinManager().createLookup(Minecraft.getInstance().getGameProfile(), false);
        return skinLookup.get();
    }

    /* ---------------- Zeichnen ---------------- */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float age = (System.currentTimeMillis() - openedAt) / 1000f;
        int alpha = (int) (255 * Mth.clamp(age * 1.6f, 0f, 1f));
        int cx = this.width / 2;

        // Server-Leiste
        int dy = dockTop();
        if (!dock.isEmpty()) DivineRender.roundRect(g, 2, dy - 4, 36, dock.size() * 32 + 4, 5, 0xCC0A0D0B);
        for (DockEntry e : dock) {
            if (e.icon() != null) g.blit(RenderPipelines.GUI_TEXTURED, e.icon(), 8, dy + 2, 0f, 0f, 24, 24, 64, 64, 64, 64);
            else DivineRender.drawEmblem(g, 8, dy + 2, 24, 255);
            DivineRender.roundRect(g, 28, dy, 6, 6, 3, 0xFF3DDC84);
            if (mouseX >= 6 && mouseX < 34 && mouseY >= dy && mouseY < dy + 28) {
                g.setTooltipForNextFrame(this.font, List.of(Ui.bold(e.data().name).getVisualOrderText(), Ui.ui(e.data().ip).getVisualOrderText()), mouseX, mouseY);
            }
            dy += 32;
        }

        // Profil oben rechts
        String name = this.minecraft.getUser().getName().toUpperCase();
        int chipW = Ui.boldWidth(name) + 34;
        int chipX = this.width - 8 - 4 * 29 - 4 - chipW;
        DivineRender.roundRect(g, chipX, 8, chipW, 24, 4, Ui.FIELD);
        PlayerFaceExtractor.extractRenderState(g, skin(), chipX + 5, 12, 16);
        Ui.boldText(g, name, chipX + 26, 16, Ui.TEXT);

        // Logo
        int lw = Math.min(240, this.width - 40);
        DivineRender.drawWordmark(g, cx - lw / 2, Math.max(36, logoY() - 22), lw, alpha);

        // Kosmetik-Karte unten rechts
        if (showPromo()) {
            int kx = this.width - 186, ky = this.height - 96;
            boolean hover = mouseX >= kx && mouseX < kx + 176 && mouseY >= ky && mouseY < ky + 84;
            DivineRender.roundRect(g, kx - 1, ky - 1, 178, 86, 5, hover ? Ui.GOLD : 0x40FFFFFF);
            DivineRender.roundGradient(g, kx, ky, 176, 84, 4, 0xF00F2A18, 0xF0050A07);
            Ui.wingPair(g, Cosmetics.wingsTexture("smaragd"), kx + 124, ky + 10, 52);
            Ui.boldText(g, "GALAXIE-", kx + 10, ky + 12, Ui.GOLD);
            Ui.boldText(g, "FLÜGEL", kx + 10, ky + 23, Ui.GOLD);
            Ui.text(g, "Capes & Flügel", kx + 10, ky + 52, Ui.TEXT_DIM);
            Ui.text(g, "für alle sichtbar", kx + 10, ky + 63, Ui.TEXT_DIM);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);

        String version = "DeuClient " + SharedConstants.getCurrentVersion().name() + " · Rechts-Shift: Mod-Menü";
        Ui.centered(g, version, cx, this.height - 14, 0x99B9BEC8);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
