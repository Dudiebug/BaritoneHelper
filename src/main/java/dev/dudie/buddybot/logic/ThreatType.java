package dev.dudie.buddybot.logic;

public enum ThreatType {
    LONG_FALL,
    SUFFOCATION_DROWNING,
    LAVA_FIRE,
    EXPLOSION_PROJECTILE,
    HOSTILE_MOB,
    STATUS_DAMAGE;

    public int priority() {
        return ordinal();
    }
}
