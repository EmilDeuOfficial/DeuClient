package eu.deusmp.deucore.compat.input;

/** Bis 1.21.8 gibt es Minecrafts Eingabe-Klassen noch nicht: gleich benannte Nachbauten fuer unsere Bildschirme. */
public record MouseButtonInfo(int button, int modifiers) {}
