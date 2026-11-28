package eu.deusmp.deucore.compat;

import eu.deusmp.deucore.compat.input.KeyEvent;
import eu.deusmp.deucore.compat.input.MouseButtonEvent;
import eu.deusmp.deucore.compat.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/**
 * Basis aller DeuClient-Bildschirme bis 1.21.8: nimmt die alten Aufrufe (x, y, Taste) an und reicht sie als
 * Ereignisse weiter, so wie der gemeinsame Quellcode sie ab 1.21.9 erwartet.
 */
public abstract class DeuScreen extends net.minecraft.client.gui.screens.Screen { // voll ausgeschrieben: nicht umbenennen
    protected DeuScreen(Component title) {
        super(title);
    }

    private static MouseButtonEvent event(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return mouseClicked(event(x, y, button), false);
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return super.mouseClicked(e.x(), e.y(), e.button());
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return mouseReleased(event(x, y, button));
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        return super.mouseReleased(e.x(), e.y(), e.button());
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return mouseDragged(event(x, y, button), dx, dy);
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return super.mouseDragged(e.x(), e.y(), e.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return keyPressed(new KeyEvent(key, scancode, modifiers));
    }

    public boolean keyPressed(KeyEvent e) {
        return super.keyPressed(e.key(), e.scancode(), e.modifiers());
    }
}
