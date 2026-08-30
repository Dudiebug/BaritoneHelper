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
    private static final Path ASSETS =
            Path.of("src/main/resources/assets/baritonehelper/models/item");
    private static final Path RECIPES =
            Path.of("src/main/resources/data/baritonehelper/recipe");

    @Test
    void publicItemsHaveExactlyThreeRecipesAndModels() throws IOException {
        Set<String> expected = Set.of(
                "worker.json",
                "worker_controller.json",
                "cargo_upgrade.json");

        assertEquals(expected, fileNames(ASSETS));
        assertEquals(expected, fileNames(RECIPES));
        assertFalse(
                Files.exists(Path.of("src/main/resources/assets/buddybot")),
                "legacy asset namespace must be removed");
        assertFalse(
                Files.exists(Path.of("src/main/resources/data/buddybot")),
                "legacy data namespace must be removed");
    }

    @Test
    void recipesUseBaritoneHelperResultsAndNoTierItems() throws IOException {
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

        assertTrue(combined.contains("\"id\": \"baritonehelper:worker\""));
        assertTrue(combined.contains("\"id\": \"baritonehelper:worker_controller\""));
        assertTrue(combined.contains("\"id\": \"baritonehelper:cargo_upgrade\""));
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
}
