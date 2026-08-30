package dev.dudie.buddybot.logic;

public record Vector3d(double x, double y, double z) {
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
