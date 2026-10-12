package net.jj.colossalturtle.walk;

import net.jj.colossalturtle.body.TurtleShape;

/**
 * Where he is in the world. Turns his own frame (the shell world) into world spots and back: x, z are his middle,
 * yaw the way he faces, baseY the world height his feet stand at, drop how far he has sunk down.
 */
public final class Pose {
    public double x, z, yaw, baseY = 64, drop;

    public void set(Pose o) {
        x = o.x;
        z = o.z;
        yaw = o.yaw;
        baseY = o.baseY;
        drop = o.drop;
    }

    public double worldX(double lx, double lz) {
        return x + lx * Math.cos(yaw) - lz * Math.sin(yaw);
    }

    public double worldZ(double lx, double lz) {
        return z + lx * Math.sin(yaw) + lz * Math.cos(yaw);
    }

    public double worldY(double ly) {
        return ly - TurtleShape.G + baseY - drop;
    }

    public double localX(double wx, double wz) {
        return (wx - x) * Math.cos(yaw) + (wz - z) * Math.sin(yaw);
    }

    public double localZ(double wx, double wz) {
        return -(wx - x) * Math.sin(yaw) + (wz - z) * Math.cos(yaw);
    }

    public double localY(double wy) {
        return wy + TurtleShape.G - baseY + drop;
    }

    /** a look direction in the world (degrees) turned into his frame */
    public float localYRot(float worldYRot) {
        return (float) (worldYRot - Math.toDegrees(yaw));
    }

    public float worldYRot(float localYRot) {
        return (float) (localYRot + Math.toDegrees(yaw));
    }
}
