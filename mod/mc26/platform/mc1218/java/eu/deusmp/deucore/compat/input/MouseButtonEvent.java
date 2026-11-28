package eu.deusmp.deucore.compat.input;

public record MouseButtonEvent(double x, double y, MouseButtonInfo info) {
    public int button() {
        return info.button();
    }
}
