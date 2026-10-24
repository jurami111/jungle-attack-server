package cr.ac.tec.jungleattack.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import cr.ac.tec.jungleattack.server.game.FogOfWar;
import cr.ac.tec.jungleattack.server.game.Match;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test
    void speciesMatchTheClientSprites() {
        assertEquals(Set.of("MONO", "MARIPOSA", "RANA", "PEREZOSO",
                "SERPIENTE", "TORTUGA", "YIGUIRRO"),
            java.util.Arrays.stream(AnimalSpecies.values())
                .map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }

    private Map<String, JsonObject> examples(String filename) throws IOException {
        Map<String, JsonObject> messages = new LinkedHashMap<>();
        Gson gson = new GsonBuilder().serializeNulls().create();
        for (String line : Files.readAllLines(Path.of("docs", filename))) {
            JsonObject message = JsonParser.parseString(line).getAsJsonObject();
            assertEquals(Set.of("type", "payload"), message.keySet());
            String type = ClientHandler.string(message, "type");
            assertTrue(message.get("payload").isJsonObject());
            assertNull(messages.put(type, message.getAsJsonObject("payload")), "Duplicate message example");
            assertEquals(message, JsonParser.parseString(gson.toJson(message)));
        }
        return messages;
    }

    @Test
    void everyDocumentedCommandHasTheActualFieldNamesAndTypes() throws IOException {
        Map<String, JsonObject> messages = examples("cliente-a-servidor.jsonl");
        assertEquals(Set.of("CREATE_MATCH", "JOIN_MATCH", "SELECT_ANIMAL",
            "MOVE_ANIMALS", "ATTACK", "VIEW_ZONE"), messages.keySet());
        ClientHandler.string(messages.get("CREATE_MATCH"), "nickname");
        ClientHandler.string(messages.get("JOIN_MATCH"), "nickname");
        assertEquals(4, ClientHandler.string(messages.get("JOIN_MATCH"), "code").length());
        AnimalSpecies.valueOf(ClientHandler.string(messages.get("SELECT_ANIMAL"), "species"));
        ClientHandler.animalIds(messages.get("MOVE_ANIMALS"));
        ClientHandler.integer(messages.get("MOVE_ANIMALS"), "targetX");
        ClientHandler.integer(messages.get("MOVE_ANIMALS"), "targetY");
        ClientHandler.animalIds(messages.get("ATTACK"));
        ClientHandler.integer(messages.get("ATTACK"), "targetId");
        ClientHandler.string(messages.get("VIEW_ZONE"), "zone");
    }

    @Test
    void snapshotsAndAllSevenSummaryEntriesMatchTheExamples() throws IOException {
        Map<String, JsonObject> messages = examples("servidor-a-cliente.jsonl");
        assertEquals(Set.of("MATCH_CREATED", "MATCH_JOINED", "PLAYER_JOINED", "ANIMAL_SELECTION",
            "GAME_STARTED", "STATE_UPDATE", "GAME_OVER", "MATCH_CANCELLED", "ERROR"), messages.keySet());
        Match match = new Match("TEST", new Random(7));
        match.start(AnimalSpecies.MONO, AnimalSpecies.MARIPOSA);
        Gson gson = new Gson();
        JsonObject state = gson.toJsonTree(new FogOfWar().createSnapshot(
            match, match.getPlayer1Pack(), null)).getAsJsonObject();
        for (String type : Set.of("GAME_STARTED", "STATE_UPDATE")) {
            JsonObject example = messages.get(type);
            assertEquals(state.keySet(), example.keySet());
            assertEquals(10, example.getAsJsonArray("ownAnimals").size());
            assertEquals(state.getAsJsonObject("ownPack").keySet(), example.getAsJsonObject("ownPack").keySet());
            assertEquals(state.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().keySet(),
                example.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().keySet());
            assertEquals(state.getAsJsonArray("visibleFlags").get(0).getAsJsonObject().keySet(),
                example.getAsJsonArray("visibleFlags").get(0).getAsJsonObject().keySet());
            assertFalse(example.has("map"));
        }
        match.finishWithWinner(match.getPlayer1Pack());
        JsonObject result = gson.toJsonTree(Map.of(
            "winner", match.getSummary().getWinnerSpecies(),
            "durationSeconds", match.getSummary().getDurationSeconds(),
            "packs", match.getSummary().getPacks())).getAsJsonObject();
        JsonObject example = messages.get("GAME_OVER");
        assertEquals(result.keySet(), example.keySet());
        assertEquals(7, example.getAsJsonArray("packs").size());
        for (int i = 0; i < 7; i++) {
            assertEquals(result.getAsJsonArray("packs").get(i).getAsJsonObject().keySet(),
                example.getAsJsonArray("packs").get(i).getAsJsonObject().keySet());
        }
    }

    @Test
    void exportedMermaidMatchesReadmeAndIncludesEveryProductionFile() throws IOException {
        String readme = Files.readString(Path.of("docs", "README.md"));
        String source = readme.substring(readme.indexOf("```mermaid\n") + "```mermaid\n".length());
        source = source.substring(0, source.indexOf("```"));
        assertEquals(source, Files.readString(Path.of("docs/diagrama.mmd")));
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                assertTrue(source.contains("class " + name + " {"), name + " is missing from the diagram");
            }
        }
    }
}
