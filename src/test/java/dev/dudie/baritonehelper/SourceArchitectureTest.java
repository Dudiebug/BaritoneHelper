package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SourceArchitectureTest {
    @Test
    void rescueTierCombatAndFollowingArchitectureIsGone() throws IOException {
        Path sourceRoot = Path.of("src/main/java/dev/dudie/baritonehelper");
        String source;
        try (var stream = Files.walk(sourceRoot)) {
            source = stream.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }

        assertFalse(source.contains("BuddyBotTier"));
        assertFalse(source.contains("RescueController"));
        assertFalse(source.contains("RescueAbility"));
        assertFalse(source.contains("ThreatType"));
        assertFalse(source.contains("FollowOwnerGoal"));
        assertFalse(source.contains("MeleeAttackGoal"));
        assertFalse(source.contains("changeDimension("));
        assertFalse(source.contains("quietPeriod"));
        assertFalse(source.contains("combatTarget"));
        assertTrue(source.contains("setInvulnerable(true)"));
        assertTrue(source.contains("public boolean isAttackable()"));
    }

    @Test
    void oldJavaPackageIsRemoved() {
        assertFalse(
                Files.exists(Path.of("src/main/java/dev/dudie/buddybot")),
                "old production package must not remain");
        assertFalse(
                Files.exists(Path.of("src/test/java/dev/dudie/buddybot")),
                "old test package must not remain");
    }
}
