package dev.dudie.buddybot.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class BuddyBotPolicyTest {
    @Test
    void tiersHaveExactRangesAndCumulativeAbilities() {
        assertEquals(16, BuddyBotTier.BASIC.range());
        assertEquals(32, BuddyBotTier.MK2.range());
        assertEquals(64, BuddyBotTier.MK3.range());

        var basic = EnumSet.of(RescueAbility.HOSTILE_DEFENSE, RescueAbility.PROJECTILE_BLOCK,
                RescueAbility.CLIFF_COBWEB, RescueAbility.ESCAPE_BREAK);
        var mk2 = EnumSet.copyOf(basic);
        mk2.addAll(EnumSet.of(RescueAbility.FALL_CLUTCH, RescueAbility.HAZARD_COVER,
                RescueAbility.SUPPORT_POTION));
        var mk3 = EnumSet.copyOf(mk2);
        mk3.addAll(EnumSet.of(RescueAbility.PEARL_REPOSITION, RescueAbility.SLOW_FALLING,
                RescueAbility.CATCH_PLATFORM, RescueAbility.EXPLOSION_SHIELD));

        for (var ability : RescueAbility.values()) {
            assertEquals(basic.contains(ability), BuddyBotTier.BASIC.supports(ability), ability.name());
            assertEquals(mk2.contains(ability), BuddyBotTier.MK2.supports(ability), ability.name());
            assertEquals(mk3.contains(ability), BuddyBotTier.MK3.supports(ability), ability.name());
        }
    }

    @Test
    void threatPriorityMatchesContract() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5},
                java.util.Arrays.stream(ThreatType.values()).mapToInt(ThreatType::priority).toArray());
    }

    @Test
    void restorationRequiresExactPlacedState() {
        assertTrue(TemporaryBlockGuard.canRestore("minecraft:cobweb", "minecraft:cobweb"));
        assertFalse(TemporaryBlockGuard.canRestore("minecraft:cobweb", "minecraft:air"));
        assertFalse(TemporaryBlockGuard.canRestore("minecraft:water", "minecraft:stone"));
        assertFalse(TemporaryBlockGuard.canRestore(null, "minecraft:water"));
    }

    @Test
    void cooldownsNeverShortenOrBecomeNegative() {
        assertEquals(40, Cooldown.start(40, 10));
        assertEquals(80, Cooldown.start(40, 80));
        assertEquals(0, Cooldown.tick(0));
        assertEquals(4, Cooldown.tick(5));
        assertEquals(0, Cooldown.start(-4, -2));
    }
}
