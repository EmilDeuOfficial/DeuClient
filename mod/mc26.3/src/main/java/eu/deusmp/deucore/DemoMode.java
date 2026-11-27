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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    private static final String[] SIDE_WINGS = {"engel", "daemon", "eis", "phoenix", "schatten", "smaragd"};
    private static int sideCount;

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
        // mit -Ddeucore.demo.world=<Ordner> direkt in diese Welt (ohne die Menue-Bilder)
        String world = System.getProperty("deucore.demo.world", "");
        if (!world.isEmpty()) {
            if (menuTicks == 40) mc.createWorldOpenFlows().openWorld(world, () -> {});
            return;
        }
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
            case 176 -> {
                cfg.crosshairStyle = 3;
                Features.all(p -> p).stream().filter(f -> f.id().equals("crosshair")).findFirst()
                        .ifPresent(f -> mc.gui.setScreen(new FeatureSettingsScreen(new DeuTitleScreen(), f)));
            }
            case 178 -> shot(mc, "04b_crosshair");
            case 180 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(new DeuTitleScreen(), mc.options));
            case 215 -> shot(mc, "05_options");
            case 220 -> mc.gui.setScreen(new DeuTitleScreen());
            case 225 -> {
                de.erik.glasshud.config.HudConfig.get().w("fps").enabled = true;
                de.erik.glasshud.config.HudConfig.get().w("keys").enabled = true;
                Features.all(p -> p).stream().filter(f -> f.id().equals("hud_fps")).findFirst()
                        .ifPresent(f -> mc.gui.setScreen(new FeatureSettingsScreen(new DeuTitleScreen(), f)));
            }
            case 255 -> shot(mc, "06_hud_settings");
            case 260 -> mc.gui.setScreen(new eu.deusmp.deucore.screen.HudMoveScreen(new DeuTitleScreen()));
            case 280 -> { // FPS knapp unter die Koordinaten ziehen: soll mit Abstand einrasten
                if (mc.gui.screen() instanceof eu.deusmp.deucore.screen.HudMoveScreen hud) hud.showDrag("fps", 7, 40);
            }
            case 285 -> shot(mc, "07a_hud_snap");
            case 290 -> shot(mc, "07_hud_move");
            case 292 -> { if (mc.gui.screen() instanceof eu.deusmp.deucore.screen.HudMoveScreen hud) hud.showMenu(150, 40); }
            case 295 -> shot(mc, "07b_hud_menu");
            case 297 -> { if (mc.gui.screen() instanceof eu.deusmp.deucore.screen.HudMoveScreen hud) hud.showMenu(30, 20); }
            case 299 -> shot(mc, "07c_hud_menu_element");
            case 300 -> Features.all(p -> p).stream().filter(f -> f.id().equals("glassscout")).findFirst()
                    .ifPresent(f -> mc.gui.setScreen(new FeatureSettingsScreen(new DeuTitleScreen(), f)));
            case 330 -> shot(mc, "08_scout_settings");
            case 331 -> Features.all(p -> p).stream().filter(f -> f.id().equals("glassscout")).findFirst()
                    .ifPresent(f -> mc.gui.setScreen(new FeatureSettingsScreen(new DeuTitleScreen(), f).showGroup("Inhalt")));
            case 332 -> shot(mc, "08b_scout_content");
            case 333 -> Features.all(p -> p).stream().filter(f -> f.id().equals("discordglass")).findFirst()
                    .ifPresent(f -> mc.gui.setScreen(new FeatureSettingsScreen(new DeuTitleScreen(), f).showGroup("Inhalt")));
            case 334 -> shot(mc, "08c_discord_content");
            case 335 -> {
                de.erik.glasshud.config.HudConfig.get().w("fps").scale = 1.7f;
                de.erik.glasshud.config.HudConfig.get().w("keys").scale = 1.4f;
                mc.gui.setScreen(new eu.deusmp.deucore.screen.HudMoveScreen(new DeuTitleScreen()));
            }
            case 365 -> {
                shot(mc, "09_hud_scaled");
                de.erik.glasshud.config.HudConfig.get().w("fps").scale = 1f;
                de.erik.glasshud.config.HudConfig.get().w("keys").scale = 1f;
            }
            case 370 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new DeuTitleScreen()));
            case 420 -> shot(mc, "10_worlds");
            case 430 -> mc.gui.setScreen(new DeuModMenuScreen(new DeuTitleScreen(), DeuModMenuScreen.Tab.COSMETICS).wings());
            case 465 -> shot(mc, "11_wings");
            case 475 -> sideCount = 0;
            case 480, 488, 496, 504, 512, 520 -> {
                cfg.wings = SIDE_WINGS[sideCount];
                cfg.cape = "";
                mc.gui.setScreen(new DeuModMenuScreen(new DeuTitleScreen(), DeuModMenuScreen.Tab.COSMETICS).wings().turned(Float.parseFloat(System.getProperty("deucore.demo.yaw", "90"))));
            }
            case 485, 493, 501, 509, 517, 525 -> shot(mc, "30_side_" + SIDE_WINGS[sideCount++]);
            case 530 -> {
                cfg.collapsedCollections = new java.util.ArrayList<>(java.util.List.of("mythos"));
                mc.gui.setScreen(new DeuModMenuScreen(new DeuTitleScreen(), DeuModMenuScreen.Tab.COSMETICS));
            }
            case 538 -> shot(mc, "31_collapsed");
            case 545 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Tooltips")) {
                @Override
                public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float delta) {
                    super.extractRenderState(g, mx, my, delta);
                    g.setComponentTooltipForNextFrame(font, java.util.List.of(
                            net.minecraft.network.chat.Component.literal("Diamantschwert").withStyle(net.minecraft.ChatFormatting.AQUA),
                            net.minecraft.network.chat.Component.literal("Changes your game mode in this world."),
                            net.minecraft.network.chat.Component.literal("Schaerfe V").withStyle(net.minecraft.ChatFormatting.GRAY)), 60, 60);
                }
            });
            case 555 -> shot(mc, "32_tooltip");
            case 560 -> { if (EXIT) mc.stop(); }
            default -> {}
        }
    }

    private static void worldPhase(Minecraft mc) {
        if (mc.player == null || Boolean.getBoolean("discordglass.demo")) return;
        worldTicks++;
        DeuConfig cfg = DeuConfig.get();
        switch (worldTicks) {
            case 1 -> {
                if (cfg.cape.isEmpty()) cfg.cape = "gold";
                if (cfg.wings.isEmpty()) cfg.wings = "engel";
                mc.gui.setScreen(null);
                mc.options.pauseOnLostFocus = false;
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
            // Elytra mit DeuClient-Cape: von hinten und schraeg von der Seite
            case 230 -> {
                mc.gui.setScreen(null);
                cfg.cape = "daemon";
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    var uuid = mc.player.getUUID();
                    server.execute(() -> {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "effect give @a minecraft:night_vision 600 0 true");
                        var player = server.getPlayerList().getPlayer(uuid);
                        if (player != null) player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                    });
                }
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 270 -> shot(mc, "12_elytra_back");
            case 300 -> shot(mc, "13_elytra_side");
            // Fluegel statt Elytra: stehen (hinten, seitlich), schleichen, gleiten, schlagen, Sturz
            case 305 -> {
                cfg.elytraWings = true;
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                cfg.wings = System.getProperty("deucore.demo.wings", "daemon");
            }
            case 330 -> shot(mc, "14_wings_stand");
            case 365 -> shot(mc, "15_wings_stand_side");
            case 370 -> mc.options.keyShift.setDown(true);
            case 400 -> shot(mc, "16_wings_sneak");
            case 405 -> {
                mc.options.keyShift.setDown(false);
                onServer(mc, "tp @a ~ ~150 ~");
            }
            case 412 -> {
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                if (server != null) server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) player.startFallFlying();
                });
                eu.deusmp.deucore.cosmetics.ElytraWings.forced = 2;
                mc.player.setXRot(18f);
            }
            case 482 -> shot(mc, "17_wings_glide");
            case 486 -> eu.deusmp.deucore.cosmetics.ElytraWings.forced = 3;
            case 504 -> shot(mc, "18_wings_flap");
            case 509 -> shot(mc, "19_wings_flap");
            case 512 -> eu.deusmp.deucore.cosmetics.ElytraWings.forced = 4;
            case 532 -> shot(mc, "20_wings_dive");
            case 536 -> {
                eu.deusmp.deucore.cosmetics.ElytraWings.forced = -1;
                onServer(mc, "tp @a ~ ~-150 ~");
                mc.gui.setScreen(new DeuModMenuScreen(null, DeuModMenuScreen.Tab.COSMETICS));
            }
            case 566 -> shot(mc, "21_cosmetics_world");
            case 570 -> onServer(mc, "advancement grant @a everything");
            case 600 -> mc.gui.setScreen(advancements(mc));
            case 615 -> shot(mc, "22_advancements");
            case 617 -> hoverAdvancement = true;
            case 625 -> shot(mc, "23_advancement_hover");
            case 627 -> { // letzten Reiter der oberen Reihe waehlen und ersten abwaehlen
                hoverAdvancement = false;
                if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen screen) {
                    screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent((screen.width - 252) / 2 + 32 + 14, (screen.height - 140) / 2 - 14,
                            new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                }
            }
            case 640 -> shot(mc, "24_advancement_tab2");
            case 645 -> { if (EXIT) mc.stop(); }
            default -> {}
        }
        if (worldTicks > 305 && worldTicks < 333) {
            mc.player.yBodyRot = mc.player.getYRot();
            mc.player.yBodyRotO = mc.player.getYRot();
        }
        if (worldTicks > 275 && worldTicks < 305 || worldTicks > 340 && worldTicks < 368) {
            // Koerper quer zur Blickrichtung drehen, damit man die Dicke der Fluegel sieht
            float yaw = mc.player.getYRot() + 70f;
            mc.player.yBodyRot = yaw;
            mc.player.yBodyRotO = yaw;
        }
    }

    private static boolean hoverAdvancement;

    /** Fortschritte-Fenster; fuer das Testbild liegt der Zeiger auf Wunsch auf dem ersten Fortschritt */
    private static net.minecraft.client.gui.screens.Screen advancements(Minecraft mc) {
        return new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(mc.player.connection.getAdvancements()) {
            @Override
            public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float delta) {
                if (hoverAdvancement) {
                    try {
                        Object tab = field(this, net.minecraft.client.gui.screens.advancements.AdvancementsScreen.class, "selectedTab");
                        var root = (net.minecraft.client.gui.screens.advancements.AdvancementWidget) field(tab, tab.getClass(), "root");
                        mx = (width - 252) / 2 + 9 + (int) Math.floor((double) field(tab, tab.getClass(), "scrollX")) + root.getX() + 13;
                        my = (height - 140) / 2 + 18 + (int) Math.floor((double) field(tab, tab.getClass(), "scrollY")) + root.getY() + 13;
                    } catch (ReflectiveOperationException e) {
                        DeuCore.LOGGER.warn("[DeuCore demo] {}", e.toString());
                    }
                }
                super.extractRenderState(g, mx, my, delta);
            }
        };
    }

    private static Object field(Object o, Class<?> type, String name) throws ReflectiveOperationException {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void onServer(Minecraft mc, String command) {
        var server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "deucore_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
        DeuCore.LOGGER.info("[DeuCore demo] Screenshot {}", name);
    }
}
