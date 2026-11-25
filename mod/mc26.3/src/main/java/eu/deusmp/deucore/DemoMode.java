package eu.deusmp.deucore;

import eu.deusmp.deucore.cosmetics.Cosmetics;
import eu.deusmp.deucore.screen.DeuModMenuScreen;
import eu.deusmp.deucore.screen.DeuTitleScreen;
import eu.deusmp.deucore.screen.FeatureSettingsScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/**
 * Nur für automatische Tests: aktiv ausschließlich mit -Ddeucore.demo=true.
 * Öffnet nacheinander Hauptmenü, Mod-Menü, Kosmetik und Einstellungen bzw. zeigt in einer Welt
 * Cape und Flügel von hinten/vorne, F3 und das Logo - und speichert jeweils einen Screenshot
 * (screenshots/deucore_*.png). Mit -Ddeucore.demo.exit=true wird das Spiel danach beendet.
 */
final class DemoMode {
    static final boolean ENABLED = Boolean.getBoolean("deucore.demo");
    private static final boolean EXIT = Boolean.getBoolean("deucore.demo.exit");
    private static int menuTicks = -1;
    private static int worldTicks = -1;

    private DemoMode() {}

    static void tick(Minecraft mc) {
        if (!ENABLED) return;
        if (mc.level == null) menuPhase(mc);
        else worldPhase(mc);
    }

    private static void menuPhase(Minecraft mc) {
        if (menuTicks < 0 && !(mc.gui.screen() instanceof DeuTitleScreen)) return;
        menuTicks++;
        DeuConfig cfg = DeuConfig.get();
        switch (menuTicks) {
            case 60 -> shot(mc, "01_title");
            case 65 -> mc.gui.setScreen(new DeuModMenuScreen(mc.gui.screen()));
            case 100 -> shot(mc, "02_modmenu");
            case 105 -> {
                if (cfg.cape.isEmpty()) cfg.cape = "gold";
                if (cfg.wings.isEmpty()) cfg.wings = "engel";
                mc.gui.setScreen(new DeuModMenuScreen(new DeuTitleScreen(), DeuModMenuScreen.Tab.COSMETICS));
            }
            case 140 -> shot(mc, "03_cosmetics");
            case 145 -> Features.all(p -> p).stream().filter(f -> f.id().equals("zoom")).findFirst()
                    .ifPresent(f -> mc.gui.setScreen(new FeatureSettingsScreen(new DeuTitleScreen(), f)));
            case 175 -> shot(mc, "04_settings");
            case 180 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(new DeuTitleScreen(), mc.options));
            case 215 -> shot(mc, "05_options");
            case 220 -> mc.gui.setScreen(new DeuTitleScreen());
            case 240 -> { if (EXIT) mc.stop(); }
            default -> {}
        }
    }

    private static void worldPhase(Minecraft mc) {
        if (mc.player == null) return;
        worldTicks++;
        DeuConfig cfg = DeuConfig.get();
        switch (worldTicks) {
            case 1 -> {
                if (cfg.cape.isEmpty()) cfg.cape = "gold";
                if (cfg.wings.isEmpty()) cfg.wings = "engel";
                mc.gui.setScreen(null);
                mc.player.setXRot(10f);
            }
            case 60 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 100 -> shot(mc, "05_back");
            case 105 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 140 -> shot(mc, "06_front");
            case 145 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.debugEntries.toggleDebugOverlay();
                mc.gui.hud.getChat().addClientSystemMessage(Cosmetics.withLogo(Component.literal(mc.getUser().getName())).copy()
                        .append(Component.literal(" - so sieht das DeuClient-Logo neben Namen aus")));
            }
            case 180 -> shot(mc, "07_f3_logo");
            case 185 -> {
                mc.debugEntries.toggleDebugOverlay();
                mc.gui.setScreen(new PauseScreen(true));
            }
            case 215 -> shot(mc, "08_pause");
            case 230 -> { if (EXIT) mc.stop(); }
            default -> {}
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "deucore_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
        DeuCore.LOGGER.info("[DeuCore demo] Screenshot {}", name);
    }
}
