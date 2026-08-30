package dev.dudie.buddybot.logic;

public final class Cooldown {
    private Cooldown() {}

    public static int start(int current, int requested) {
        return Math.max(Math.max(0, current), Math.max(0, requested));
    }

    public static int tick(int current) {
        return Math.max(0, current - 1);
    }
}
