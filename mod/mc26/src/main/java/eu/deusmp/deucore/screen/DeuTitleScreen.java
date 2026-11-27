package eu.deusmp.deucore.screen;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.DeuCore;
import eu.deusmp.deucore.DeuRender;
import eu.deusmp.deucore.cosmetics.Cosmetics;
import eu.deusmp.deucore.screen.Ui.IconButton;
import eu.deusmp.deucore.screen.Ui.IconButton.Style;
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
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Deu-Hauptmenü im cleanen Client-Look:
 *  - Mitte: Logo "DEUCLIENT", Haupt-Buttons mit Icons, "Spiel beenden"
 *  - oben rechts: Profil (Kopf + Name) und quadratische Schnellzugriffe
 *  - links: Server-Leiste mit den Servern aus der Multiplayer-Liste (Klick = verbinden)
 *  - unten rechts: Kosmetik-Karte
 */
public class DeuTitleScreen extends Screen {
    private static final int BW = 170;
    private static final int PW = 164, PH = 80; // Karte unten rechts
    private static Supplier<PlayerSkin> skinLookup;
    private String dockTip;
    private final long openedAt = System.currentTimeMillis();
    private int bh = 22, gap = 6;
    private final List<DockEntry> dock = new ArrayList<>();
    /** Pingt die Server der Leiste: der gruene Punkt erscheint nur, wenn der Server antwortet */
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private List<ServerData> pinged;

    private record DockEntry(ServerData data, DeuRender.PixelImage icon) {}

    public DeuTitleScreen() {
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
        addRenderableWidget(new IconButton(x, y, BW, bh, Ui.I_GRID, Component.literal("Mod-Menü"),
                b -> this.minecraft.gui.setScreen(new DeuModMenuScreen(this))));
        y += bh + gap;
        addRenderableWidget(new IconButton(x, y, BW, bh, Ui.I_WINGS, Component.literal("KOSMETIK"), Style.FEATURE, 0,
                b -> this.minecraft.gui.setScreen(new DeuModMenuScreen(this, DeuModMenuScreen.Tab.COSMETICS))));
        y += bh + gap;
        y += 12;

        int qy = Math.min(y, this.height - 42);
        addRenderableWidget(new IconButton(cx - 54, qy, 108, 20, Ui.I_POWER, Component.literal("SPIEL BEENDEN"), Style.DANGER, 0, b -> this.minecraft.stop()));

        // Schnellzugriff oben rechts (quadratisch)
        int s = 24, qx = this.width - 8 - s;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_LANGUAGE, Component.empty(), b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager()))));
        qx -= s + 5;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_GEAR, Component.empty(), b -> this.minecraft.gui.setScreen(eu.deusmp.deucore.compat.Platform.optionsScreen(this, this.minecraft.options))));
        qx -= s + 5;
        addRenderableWidget(new IconButton(qx, 8, s, s, Ui.I_PAINT_BRUSH, Component.empty(), b -> eu.deusmp.deucore.Features.all(p -> p).stream()
                .filter(f -> f.id().equals("theme")).findFirst().ifPresent(f -> this.minecraft.gui.setScreen(new FeatureSettingsScreen(this, f)))));

        // Server-Leiste links
        loadDock();
        int dy = dockTop();
        for (DockEntry e : dock) {
            addRenderableWidget(new IconButton(6, dy, 28, 28, null, Component.empty(), Style.GHOST, 0, b -> join(e.data())));
            dy += 32;
        }

        if (showPromo()) {
            addRenderableWidget(new IconButton(this.width - PW - 10, this.height - PH - 12, PW, PH, null, Component.empty(), Style.GHOST, 0,
                    b -> this.minecraft.gui.setScreen(new DeuModMenuScreen(this, DeuModMenuScreen.Tab.COSMETICS))));
        }
    }

    /* ---------------- Server-Leiste ---------------- */

    private void loadDock() {
        dock.clear();
        try {
            if (pinged == null) {
                ServerList list = new ServerList(this.minecraft);
                list.load();
                pinged = new ArrayList<>();
                for (int i = 0; i < Math.min(list.size(), 6); i++) {
                    ServerData sd = list.get(i);
                    pinged.add(sd);
                    try {
                        sd.setState(ServerData.State.PINGING);
                        pinger.pingServer(sd, () -> { }, () -> { },
                                EventLoopGroupHolder.remote(this.minecraft.options.useNativeTransport()));
                    } catch (Exception ex) {
                        sd.setState(ServerData.State.UNREACHABLE);
                    }
                }
            }
            int max = Math.min(pinged.size(), Math.max(1, (this.height - 80) / 32));
            for (int i = 0; i < Math.min(max, 6); i++) {
                ServerData sd = pinged.get(i);
                DeuRender.PixelImage icon = null;
                byte[] bytes = sd.getIconBytes();
                if (bytes != null) {
                    try {
                        icon = new DeuRender.PixelImage("dock" + i, NativeImage.read(bytes));
                    } catch (Exception ex) {
                        icon = null;
                    }
                }
                dock.add(new DockEntry(sd, icon));
            }
        } catch (Exception e) {
            DeuCore.LOGGER.debug("Serverliste nicht lesbar", e);
        }
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
    }

    @Override
    public void removed() {
        pinger.removeAll();
        super.removed();
    }

    private int dockTop() {
        return this.height / 2 - dock.size() * 32 / 2;
    }

    private void join(ServerData data) {
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    private boolean showPromo() {
        return this.width / 2 + BW / 2 + 14 < this.width - PW - 10 && this.height > 220;
    }

    private PlayerSkin skin() {
        return Ui.selfSkin();
    }

    /* ---------------- Zeichnen ---------------- */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float age = (System.currentTimeMillis() - openedAt) / 1000f;
        int alpha = (int) (255 * Mth.clamp(age * 1.6f, 0f, 1f));
        int cx = this.width / 2;

        // Server-Leiste
        dockTip = null;
        int dy = dockTop();
        if (!dock.isEmpty()) DeuRender.roundRect(g, 2, dy - 4, 36, dock.size() * 32 + 4, 5, Ui.BG);
        for (DockEntry e : dock) {
            if (e.icon() != null) DeuRender.drawRounded(g, e.icon(), 8, dy + 2, 24, 0.2);
            else DeuRender.drawEmblem(g, 8, dy + 2, 24, 255);
            DeuRender.roundRect(g, 28, dy, 6, 6, 3, e.data().state() == ServerData.State.SUCCESSFUL ? 0xFF3DDC84 : 0xFF6B7280);
            if (mouseX >= 6 && mouseX < 34 && mouseY >= dy && mouseY < dy + 28) {
                dockTip = e.data().name + "\n" + e.data().ip;
            }
            dy += 32;
        }

        // Profil oben rechts
        String name = this.minecraft.getUser().getName();
        int chipW = Ui.boldWidth(name) + 34;
        int chipX = this.width - 8 - 3 * 29 - chipW;
        DeuRender.roundRect(g, chipX, 8, chipW, 24, 4, Ui.FIELD);
        PlayerFaceExtractor.extractRenderState(g, skin(), chipX + 5, 12, 16);
        Ui.boldText(g, name, chipX + 26, 16, Ui.TEXT);

        // Logo
        int lw = Math.min(240, this.width - 40);
        DeuRender.drawWordmark(g, cx - lw / 2, Math.max(36, logoY() - 22), lw, alpha);

        // Kollektions-Karte unten rechts
        if (showPromo()) {
            int kx = this.width - PW - 10, ky = this.height - PH - 12;
            boolean hover = mouseX >= kx && mouseX < kx + PW && mouseY >= ky && mouseY < ky + PH;
            if (hover) DeuRender.roundRect(g, kx - 1, ky - 1, PW + 2, PH + 2, 6, Ui.ACCENT);
            DeuRender.roundRect(g, kx, ky, PW, PH, 5, Ui.CARD);
            Ui.boldText(g, Cosmetics.COLLECTION, kx + 9, ky + 8, Ui.TEXT);
            Ui.text(g, "Capes & Flügel", kx + 9, ky + 19, Ui.TEXT_DIM);
            int i = 0;
            for (String id : new String[]{"gold", "nacht", "daemon"}) Ui.capeFront(g, id, kx + 9 + i++ * 23, ky + 34, 20, 32);
            Ui.wingPair(g, Cosmetics.wingsUi("smaragd"), kx + PW - 43, ky + 34, 34);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
        if (dockTip != null) Ui.tooltip(g, mouseX, mouseY, dockTip, 220, this.width, this.height);

        String version = "DeuClient " + SharedConstants.getCurrentVersion().name();
        Ui.centered(g, version, cx, this.height - 14, 0x80B9BEC8);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
