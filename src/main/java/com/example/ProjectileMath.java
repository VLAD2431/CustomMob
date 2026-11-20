package com.example;

final class ProjectileMath {
    private ProjectileMath() {}

    static double[] highArc(double x, double y, double z, double speed, double gravity) {
        double horizontal = Math.hypot(x, z);
        if (speed <= 0 || !Double.isFinite(speed)) return new double[] {0, 0, 0};
        if (gravity <= 0 || !Double.isFinite(gravity)) {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length > 0 ? new double[] {x * speed / length, y * speed / length, z * speed / length} : new double[] {0, 0, 0};
        }
        if (horizontal < 1.0e-9) return new double[] {0, speed + gravity / 2, 0};
        double speedSquared = speed * speed;
        double discriminant = speedSquared * speedSquared - gravity * (gravity * horizontal * horizontal + 2 * y * speedSquared);
        double tangent;
        double horizontalSpeed;
        if (discriminant >= 0) {
            double highTangent = (speedSquared + Math.sqrt(discriminant)) / (gravity * horizontal);
            double lowTangent = (speedSquared - Math.sqrt(discriminant)) / (gravity * horizontal);
            tangent = Math.max(lowTangent, Math.min(Math.sqrt(3 + 2 * Math.sqrt(2)), highTangent));
            horizontalSpeed = Math.sqrt(gravity * horizontal * horizontal / (2 * (horizontal * tangent - y)));
        } else {
            // Maximum-range angle for the target height when the configured speed cannot reach it.
            tangent = speedSquared / (gravity * horizontal);
            horizontalSpeed = speed / Math.hypot(1, tangent);
        }
        return new double[] {x / horizontal * horizontalSpeed, horizontalSpeed * tangent + gravity / 2, z / horizontal * horizontalSpeed};
    }
}
