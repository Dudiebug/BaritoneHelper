package dev.dudie.buddybot.logic;

import java.util.EnumSet;

public enum BuddyBotTier {
    BASIC(16, RescueAbility.HOSTILE_DEFENSE, RescueAbility.PROJECTILE_BLOCK,
            RescueAbility.CLIFF_COBWEB, RescueAbility.ESCAPE_BREAK),
    MK2(32, RescueAbility.HOSTILE_DEFENSE, RescueAbility.PROJECTILE_BLOCK,
            RescueAbility.CLIFF_COBWEB, RescueAbility.ESCAPE_BREAK,
            RescueAbility.FALL_CLUTCH, RescueAbility.HAZARD_COVER,
            RescueAbility.SUPPORT_POTION),
    MK3(64, RescueAbility.values());

    private final int range;
    private final EnumSet<RescueAbility> abilities;

    BuddyBotTier(int range, RescueAbility... abilities) {
        this.range = range;
        this.abilities = abilities.length == 0
                ? EnumSet.noneOf(RescueAbility.class)
                : EnumSet.of(abilities[0], abilities);
    }

    public int range() {
        return range;
    }

    public boolean supports(RescueAbility ability) {
        return abilities.contains(ability);
    }
}
