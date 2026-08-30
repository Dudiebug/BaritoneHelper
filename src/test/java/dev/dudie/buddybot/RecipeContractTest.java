package dev.dudie.buddybot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RecipeContractTest {
    private static String recipe(String name) throws IOException {
        var path = Path.of("src/main/resources/data/buddybot/recipe", name + ".json");
        assertTrue(Files.isRegularFile(path), path.toString());
        return Files.readString(path).replaceAll("\\s+", "");
    }

    @Test
    void recipesHaveExactPatternsAndUpgradeIngredients() throws IOException {
        var basic = recipe("buddy_bot");
        assertEquals("IRI/RPR/IRI", pattern(basic));
        assertEquals("minecraft:iron_ingot", key(basic, "I"));
        assertEquals("minecraft:redstone", key(basic, "R"));
        assertEquals("minecraft:carved_pumpkin", key(basic, "P"));

        var mk2 = recipe("buddy_bot_mk2");
        assertEquals("GDG/BIB/GOG", pattern(mk2));
        assertEquals("buddybot:buddy_bot", key(mk2, "I"));
        assertEquals("minecraft:observer", key(mk2, "O"));

        var mk3 = recipe("buddy_bot_mk3");
        assertEquals("ONO/EIE/OTO", pattern(mk3));
        assertEquals("buddybot:buddy_bot_mk2", key(mk3, "I"));
        assertEquals("minecraft:nether_star", key(mk3, "N"));
        assertEquals("minecraft:totem_of_undying", key(mk3, "T"));
    }

    private static String pattern(String recipe) {
        var matcher = Pattern.compile("\\\"pattern\\\":\\[\\\"([^\\\"]+)\\\",\\\"([^\\\"]+)\\\",\\\"([^\\\"]+)\\\"]")
                .matcher(recipe);
        assertTrue(matcher.find(), recipe);
        return matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3);
    }

    private static String key(String recipe, String symbol) {
        var matcher = Pattern.compile("\\\"" + symbol + "\\\":\\{\\\"item\\\":\\\"([^\\\"]+)\\\"}")
                .matcher(recipe);
        assertTrue(matcher.find(), recipe);
        return matcher.group(1);
    }
}
