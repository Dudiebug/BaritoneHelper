package dev.dudie.buddybot.logic;

import java.util.Optional;

public final class RescueMath {
    private RescueMath() {}

    public static double highestLandingY(double... candidates) {
        double highest = Double.NaN;
        for (double candidate : candidates) {
            if (Double.isFinite(candidate) && (Double.isNaN(highest) || candidate > highest)) {
                highest = candidate;
            }
        }
        return highest;
    }

    public static boolean isDangerousCliff(double[] drops) {
        if (drops == null || drops.length != 8) return false;
        int dangerous = 0;
        for (double drop : drops) {
            if (Double.isFinite(drop) && drop > 4.0 && ++dangerous >= 3) return true;
        }
        return false;
    }

    public static Optional<Vector3d> ballisticVelocity(
            Vector3d from, Vector3d to, double speed, double gravity) {
        if (from == null || to == null || !(speed > 0) || !(gravity > 0)) return Optional.empty();
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1e-9) return Optional.empty();

        double dy = to.y() - from.y();
        double speed2 = speed * speed;
        double discriminant = speed2 * speed2
                - gravity * (gravity * horizontal * horizontal + 2.0 * dy * speed2);
        if (discriminant < 0 || !Double.isFinite(discriminant)) return Optional.empty();

        double tan = (speed2 - Math.sqrt(discriminant)) / (gravity * horizontal);
        double horizontalSpeed = speed / Math.sqrt(1.0 + tan * tan);
        double scale = horizontalSpeed / horizontal;
        return Optional.of(new Vector3d(dx * scale, horizontalSpeed * tan, dz * scale));
    }
}
