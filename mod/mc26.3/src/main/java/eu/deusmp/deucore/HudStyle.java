package eu.deusmp.deucore;

import de.erik.discordglass.config.GlassConfig;
import de.erik.glasshud.config.HudConfig;
import de.erik.glasshud.hud.widgets.Widget;
import de.erik.glasshud.hud.widgets.Widgets;
import de.erik.glassscout.config.ScoutConfig;
import eu.deusmp.deucore.Features.Kind;
import eu.deusmp.deucore.Features.Setting;

import java.util.List;

/** Darstellung (Groesse, Hintergrund, Transparenz) der HUD-Elemente, des Scouts und von Discord - fuer alle gleich. */
public final class HudStyle {
    private HudStyle() {}

    public static final String SCOUT = "scout";
    public static final String DISCORD = "discord";
    public static final String GROUP = "Darstellung";

    private record Look(float scale, int bg, int transparency) {}

    private static Look get(String id) {
        if (SCOUT.equals(id)) {
            ScoutConfig c = ScoutConfig.get();
            return new Look(c.scale, c.bgStyle, c.bgTransparency);
        }
        if (DISCORD.equals(id)) {
            GlassConfig c = GlassConfig.get();
            return new Look(c.scale, c.bgStyle, c.bgTransparency);
        }
        HudConfig.W w = HudConfig.get().w(id);
        return new Look(w.scale, w.bgStyle, w.bgTransparency);
    }

    private static void put(String id, Look l) {
        if (SCOUT.equals(id)) {
            ScoutConfig c = ScoutConfig.get();
            c.scale = l.scale();
            c.bgStyle = l.bg();
            c.bgTransparency = l.transparency();
        } else if (DISCORD.equals(id)) {
            GlassConfig c = GlassConfig.get();
            c.scale = l.scale();
            c.bgStyle = l.bg();
            c.bgTransparency = l.transparency();
        } else {
            HudConfig.W w = HudConfig.get().w(id);
            w.scale = l.scale();
            w.bgStyle = l.bg();
            w.bgTransparency = l.transparency();
        }
    }

    private static void save() {
        HudConfig.save();
        ScoutConfig.save();
        GlassConfig.save();
    }

    private static void change(String id, Look l) {
        put(id, l);
        save();
    }

    /** Die drei Einstellungen der Unterkategorie "Darstellung" */
    public static List<Setting> settings(String id) {
        return List.of(
                new Setting("Größe", Kind.STEP, null, 50, 300, 10, " %", () -> Math.round(get(id).scale() * 100),
                        v -> change(id, new Look(v / 100f, get(id).bg(), get(id).transparency()))).in(GROUP),
                new Setting("Hintergrund", Kind.CYCLE, new String[]{"Aus", "Dunkel", "Schwarz"}, 0, 2, 1, "", () -> get(id).bg(),
                        v -> change(id, new Look(get(id).scale(), v, get(id).transparency()))).in(GROUP),
                new Setting("Transparenz", Kind.STEP, null, 0, 90, 10, " %", () -> get(id).transparency(),
                        v -> change(id, new Look(get(id).scale(), get(id).bg(), v)), () -> get(id).bg() != 0).in(GROUP));
    }

    /** Uebertraegt die Darstellung dieses Elements auf alle HUD-Elemente, den Scout und Discord */
    public static void applyToAll(String sourceId) {
        Look l = get(sourceId);
        for (Widget w : Widgets.ALL) put(w.id, l);
        put(SCOUT, l);
        put(DISCORD, l);
        save();
    }
}
