package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecipeAndAssetContractTest {
    private static final Path MODELS =
            Path.of("src/main/resources/assets/baritonehelper/models/item");
    private static final Path RECIPES =
            Path.of("src/main/resources/data/baritonehelper/recipe");
    private static final Path LEGACY_ASSETS =
            Path.of("src/main/resources/assets/buddybot");

    @Test
    void canonicalGameplaySurfaceHasExactlyThreeRecipesAndModels() throws IOException {
        Set<String> expected = Set.of(
                "baritone_helper.json",
                "worker_controller.json",
                "cargo_upgrade.json");

        assertEquals(expected, fileNames(MODELS));
        assertEquals(expected, fileNames(RECIPES));
        assertFalse(
                Files.exists(Path.of("src/main/resources/data/buddybot")),
                "legacy recipes and data must be removed");
    }

    @Test
    void legacyAssetsContainOnlyTheBaseItemCompatibilityModel() throws IOException {
        assertEquals(
                Set.of("models/item/buddy_bot.json"),
                relativeFiles(LEGACY_ASSETS));
    }

    @Test
    void recipesUseCanonicalIdsAndNoTierItems() throws IOException {
        String combined = Files.walk(RECIPES)
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .collect(Collectors.joining("\n"));

        assertTrue(combined.contains("\"id\": \"baritonehelper:baritone_helper\""));
        assertTrue(combined.contains("\"id\": \"baritonehelper:worker_controller\""));
        assertTrue(combined.contains("\"id\": \"baritonehelper:cargo_upgrade\""));
        assertFalse(combined.contains("\"id\": \"baritonehelper:worker\""));
        assertFalse(combined.contains("buddy_bot_mk2"));
        assertFalse(combined.contains("buddy_bot_mk3"));
    }

    private static Set<String> fileNames(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), directory.toString());
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> relativeFiles(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), directory.toString());
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .map(directory::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .collect(Collectors.toSet());
        }
    }
}
