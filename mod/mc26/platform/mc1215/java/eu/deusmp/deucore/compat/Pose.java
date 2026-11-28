package eu.deusmp.deucore.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * Bis 1.21.5 rechnet die Oberflaeche mit einem 3D-Stapel. Diese Huelle bietet die 2D-Befehle an, die der
 * gemeinsame Quellcode benutzt; die Tiefe (Ebene des Bildschirms) bleibt dabei erhalten.
 */
public final class Pose {
    private final PoseStack stack;

    private Pose(PoseStack stack) {
        this.stack = stack;
    }

    public static Pose of(GuiGraphics graphics) {
        return new Pose(graphics.pose());
    }

    public Pose pushMatrix() {
        stack.pushPose();
        return this;
    }

    public Pose popMatrix() {
        stack.popPose();
        return this;
    }

    public Pose translate(float x, float y) {
        stack.translate(x, y, 0f);
        return this;
    }

    public Pose scale(float x, float y) {
        stack.scale(x, y, 1f);
        return this;
    }

    public Pose scale(float s) {
        return scale(s, s);
    }

    /** Ebene nach vorn (nur bis 1.21.5, z. B. fuer Tooltips) */
    public Pose translateDepth(float z) {
        stack.translate(0f, 0f, z);
        return this;
    }

    public Pose rotate(float radians) {
        stack.mulPose(Axis.ZP.rotation(radians));
        return this;
    }

    public Pose identity() {
        float depth = matrix().m32();
        stack.setIdentity();
        stack.translate(0f, 0f, depth);
        return this;
    }

    private Matrix4f matrix() {
        return stack.last().pose();
    }

    public float m00() {
        return matrix().m00();
    }

    public float m01() {
        return matrix().m01();
    }

    public float m10() {
        return matrix().m10();
    }

    public float m11() {
        return matrix().m11();
    }

    /** Verschiebung nach rechts */
    public float m20() {
        return matrix().m30();
    }

    /** Verschiebung nach unten */
    public float m21() {
        return matrix().m31();
    }
}
