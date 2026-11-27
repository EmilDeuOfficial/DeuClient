package eu.deusmp.deucore;

import eu.deusmp.deucore.screen.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Hilfsfunktionen der eingebauten Module (HUD, Discord, Scout): glatte DeuClient-Schrift und Icons. */
public final class Fx {
    private Fx() {}

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.text(Ui.font(), Ui.ui(s, g), x, y, color, false); // kein Schatten im DeuClient-Stil
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, color, shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, color, shadow);
    }

    public static int width(Font font, String s) {
        return Ui.width(s);
    }

    public static int width(Font font, FormattedCharSequence s) {
        return font.width(s);
    }

    /** Exakte Textbreite (Gleitkomma) */
    public static float widthF(String s) {
        return Ui.widthF(s);
    }

    /** Text an Gleitkomma-Position, auf Bildschirm-Pixel gerastert (scharf, exakt zentriert) */
    public static void textAt(GuiGraphicsExtractor g, String s, float x, float y, int color) {
        Ui.textAt(g, s, x, y, color);
    }

    /** Scharfes Icon aus den SVG-Vorlagen (icons_hd), size in GUI-Pixeln */
    public static void iconSharp(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
        Ui.iconSharp(g, name, x, y, size, color);
    }

    public static String ellipsize(Font font, String s, int maxW) {
        if (s == null || maxW <= 0) return "";
        if (width(font, s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(font, t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    /** Zeichnet das glatte Icon "name" statt eines Pixel-Icons (Mitte = Mitte des alten Icons) */
    public static void icon(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int color) {
        int size = Math.max(w, h) + 3;
        Ui.iconCentered(g, name, x + w / 2, y + h / 2, size, color);
    }
}
