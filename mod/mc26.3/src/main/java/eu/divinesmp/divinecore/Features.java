package eu.divinesmp.divinecore;

import com.mojang.blaze3d.platform.InputConstants;
import eu.divinesmp.divinecore.GlassMods.GlassMod;
import eu.divinesmp.divinecore.net.DivineNetwork;
import eu.divinesmp.divinecore.screen.Ui;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Alle Funktionen des DeuClients, wie sie im Mod-Menü als Karten erscheinen.
 * Jede Funktion hat optional einen An/Aus-Schalter, Einstellungen und ein eigenes Menü.
 */
public final class Features {
    private Features() {}

    public enum Category {
        ALL("Alle"), FAVORITES("Favoriten"), DIVINE("Optik"), CLIENT("Client"), COSMETICS("Kosmetik"), GLASS("Module");
        public final String label;
        Category(String label) { this.label = label; }
    }

    public enum Kind { TOGGLE, CYCLE, STEP }

    /** Eine einzelne Einstellung (Zeile im Einstellungs-Bildschirm) */
    public record Setting(String label, Kind kind, String[] names, int min, int max, int step, String suffix, IntSupplier get, IntConsumer set) {
        public String display() {
            int v = get.getAsInt();
            if (v < 0) return "–";
            return switch (kind) {
                case TOGGLE -> v != 0 ? "§aAn" : "§cAus";
                case CYCLE -> names[Math.floorMod(v, names.length)];
                case STEP -> v + suffix;
            };
        }

        /** Klick: Schalter umlegen bzw. zum nächsten Wert (dir = +1/-1) */
        public void cycle(int dir) {
            int v = get.getAsInt();
            int next = switch (kind) {
                case TOGGLE -> v != 0 ? 0 : 1;
                case CYCLE -> Math.floorMod(v + dir, names.length);
                case STEP -> Math.max(min, Math.min(max, v + dir * step));
            };
            set.accept(next);
        }
    }

    public record Feature(String id, String name, String description, Category category, String icon, boolean isNew,
                          BooleanSupplier isOn, Consumer<Boolean> setOn, List<Setting> settings, Function<Screen, Screen> customScreen) {
        public boolean hasToggle() { return isOn != null && setOn != null; }
        public boolean isFavorite() { return DivineConfig.get().favorites.contains(id); }
        public void toggleFavorite() {
            List<String> fav = DivineConfig.get().favorites;
            if (!fav.remove(id)) fav.add(id);
            DivineConfig.save();
        }
    }

    /* ---------------- Helfer für Felder in DivineConfig ---------------- */

    private static Field field(String name) {
        try {
            return DivineConfig.class.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BooleanSupplier bool(String name) {
        Field f = field(name);
        return () -> { try { return f.getBoolean(DivineConfig.get()); } catch (IllegalAccessException e) { return false; } };
    }

    private static Consumer<Boolean> setBool(String name, boolean sync) {
        Field f = field(name);
        return v -> {
            try { f.setBoolean(DivineConfig.get(), v); } catch (IllegalAccessException ignored) {}
            DivineConfig.save();
            if (sync) DivineNetwork.sendHello();
        };
    }

    private static Setting toggle(String label, String name) {
        BooleanSupplier g = bool(name);
        Consumer<Boolean> s = setBool(name, false);
        return new Setting(label, Kind.TOGGLE, null, 0, 1, 1, "", () -> g.getAsBoolean() ? 1 : 0, v -> s.accept(v != 0));
    }

    private static Setting cycle(String label, String name, String... names) {
        Field f = field(name);
        return new Setting(label, Kind.CYCLE, names, 0, names.length - 1, 1, "",
                () -> { try { return f.getInt(DivineConfig.get()); } catch (IllegalAccessException e) { return 0; } },
                v -> { try { f.setInt(DivineConfig.get(), v); } catch (IllegalAccessException ignored) {} DivineConfig.save(); });
    }

    private static final int[] ZOOM_KEYS = {InputConstants.KEY_C, InputConstants.KEY_V, InputConstants.KEY_X, InputConstants.KEY_Z, InputConstants.KEY_B, InputConstants.KEY_LALT};
    private static final String[] ZOOM_KEY_NAMES = {"C", "V", "X", "Z", "B", "Alt"};

    private static Feature divine(String id, String name, String desc, String icon, String field, Setting... extra) {
        return new Feature(id, name, desc, Category.DIVINE, icon, false, bool(field), setBool(field, false), List.of(extra), null);
    }

    /** Baut die Liste aller Funktionen (Glass-Mods nur, wenn installiert) */
    public static List<Feature> all(Function<Screen, Screen> cosmeticsScreen) {
        List<Feature> list = new ArrayList<>();
        // ---- Client
        list.add(new Feature("f3name", "F3-Name", "Zeigt \"DeuClient\" und deinen Spielernamen oben im F3-Bildschirm.",
                Category.CLIENT, Ui.I_INFO, true, bool("f3Name"), setBool("f3Name", false), List.of(), null));
        list.add(new Feature("logonames", "DeuClient-Logo", "DeuClient-Logo neben den Namen aller Spieler, die den DeuClient nutzen – über dem Kopf und in der Tab-Liste.",
                Category.CLIENT, Ui.I_SPARKLE, true, bool("logoNames"), setBool("logoNames", false), List.of(), null));
        list.add(new Feature("ownname", "Eigener Name", "Zeigt in der Ansicht von hinten/vorne (F5) deinen Namen über deinem Kopf – so wie andere Spieler ihn sehen.",
                Category.CLIENT, Ui.I_TAG, true, bool("ownNametag"), setBool("ownNametag", false), List.of(), null));
        Field zoomKey = field("zoomKey");
        list.add(new Feature("zoom", "Zoom", "Taste gedrückt halten zum Heranzoomen – wie ein Fernglas.",
                Category.CLIENT, Ui.I_ZOOM, true, bool("zoom"), setBool("zoom", false), List.of(
                cycle("Zoom-Stufe", "zoomLevel", Zoom.LEVEL_NAMES),
                toggle("Weicher Zoom", "zoomSmooth"),
                new Setting("Taste", Kind.CYCLE, ZOOM_KEY_NAMES, 0, ZOOM_KEYS.length - 1, 1, "",
                        () -> { try { int k = zoomKey.getInt(DivineConfig.get()); for (int i = 0; i < ZOOM_KEYS.length; i++) if (ZOOM_KEYS[i] == k) return i; } catch (IllegalAccessException ignored) {} return 0; },
                        v -> { try { zoomKey.setInt(DivineConfig.get(), ZOOM_KEYS[Math.floorMod(v, ZOOM_KEYS.length)]); } catch (IllegalAccessException ignored) {} DivineConfig.save(); })),
                null));
        list.add(new Feature("guiscale", "GUI-Skalierung", "Größe der Oberfläche, auch 2,5. Standard ist 2,5; \"Auto\" nutzt die Videoeinstellung von Minecraft.",
                Category.CLIENT, Ui.I_WINDOW, true, bool("customGuiScale"), setBool("customGuiScale", false), List.of(
                cycle("Stufe", "guiScale", DivineConfig.GUI_SCALE_NAMES)), null));
        list.add(new Feature("crosshair", "Fadenkreuz", "Eigenes Fadenkreuz in DeuClient-Farben und verschiedenen Formen.",
                Category.CLIENT, Ui.I_CROSS, true, bool("customCrosshair"), setBool("customCrosshair", false), List.of(
                cycle("Form", "crosshairStyle", Crosshair.STYLES),
                cycle("Farbe", "crosshairColor", Crosshair.COLOR_NAMES)), null));

        // ---- Kosmetik
        list.add(new Feature("capes", "Capes", "DeuClient-Capes – sichtbar für alle DeuClient-Spieler. Auch auf der Elytra.",
                Category.COSMETICS, Ui.I_CAPE, true, bool("showCosmetics"), setBool("showCosmetics", true), List.of(), cosmeticsScreen));
        list.add(new Feature("wings", "Flügel", "DeuClient-Flügel am Rücken – sichtbar für alle DeuClient-Spieler.",
                Category.COSMETICS, Ui.I_WINGS, true, bool("wingsAnimated"), setBool("wingsAnimated", false), List.of(
                toggle("Flügelschlag-Animation", "wingsAnimated")), cosmeticsScreen));

        // ---- DeuClient-Optik
        list.add(divine("title", "Hauptmenü", "DeuClient-Hauptmenü statt des Minecraft-Titelbildschirms.", Ui.I_HOME, "customTitleScreen"));
        list.add(divine("loading", "Ladebildschirm", "DeuClient-Ladebildschirm statt Mojang-Logo.", Ui.I_HOURGLASS, "customLoadingScreen"));
        list.add(divine("buttons", "DeuClient-Buttons", "Abgerundete Buttons in Grün und Schwarz – überall im Spiel.", Ui.I_BUTTON, "customButtons"));
        list.add(divine("background", "Hintergrund", "Eigener Hintergrund in allen Menüs: verschwommenes Welt-Panorama oder DeuClient-Animation.", Ui.I_PAINT, "customBackground",
                toggle("Welt-Panorama (aus = DeuClient-Animation)", "panoramaBackground"),
                toggle("Goldpartikel", "particles")));
        list.add(divine("pause", "Pause-Menü", "DeuClient-Overlay, Schriftzug und Mod-Menü-Button im Pause-Menü.", Ui.I_SPARKLE, "customPauseMenu"));
        list.add(divine("windowtitle", "Fenstertitel", "\"DeuClient - Minecraft <Version>\" als Fenstertitel.", Ui.I_WINDOW, "customWindowTitle"));

        // ---- Glass-Mods
        for (GlassMod m : GlassMods.ALL) {
            if (!m.installed()) continue;
            List<Setting> settings = new ArrayList<>();
            GlassMods.Option master = null;
            for (GlassMods.Option o : m.options()) {
                if (o.field().equals("hudEnabled")) master = o;
                Kind k = switch (o.kind()) { case TOGGLE -> Kind.TOGGLE; case CYCLE -> Kind.CYCLE; case PERCENT -> Kind.STEP; };
                settings.add(new Setting(o.label(), k, o.names(), o.min(), o.max(), o.step(), k == Kind.STEP ? " %" : "",
                        () -> m.get(o), v -> m.set(o, v)));
            }
            final GlassMods.Option mo = master;
            String icon = switch (m.modId()) {
                case "glasshud" -> Ui.I_HUD;
                case "discordglass" -> Ui.I_CHAT;
                default -> Ui.I_EYE;
            };
            list.add(new Feature(m.modId(), m.name(), m.description() + " Taste: " + m.hotkey(), Category.GLASS, icon, false,
                    mo == null ? null : () -> m.get(mo) == 1, mo == null ? null : v -> m.set(mo, v ? 1 : 0),
                    settings, m.screenClass() == null ? null : m::createScreen));
        }
        return list;
    }
}
