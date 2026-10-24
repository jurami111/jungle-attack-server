package cr.ac.tec.jungleattack.server.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ServerTest {
    private Server server;
    private Thread serverThread;
    private final List<Peer> peers = new ArrayList<>();
    private final AtomicReference<IOException> serverFailure = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new Server(0);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException exception) {
                serverFailure.set(exception);
            }
        });
        serverThread.start();
    }

    @AfterEach
    void stopServer() throws IOException, InterruptedException {
        server.close();
        for (Peer peer : peers) peer.close();
        serverThread.join(3000);
        assertFalse(serverThread.isAlive(), "Server must stop accepting connections");
        assertNull(serverFailure.get());
    }

    private Peer connect() throws IOException {
        Peer peer = new Peer(server.getPort());
        peers.add(peer);
        return peer;
    }

    private String join(Peer first, Peer second) throws IOException {
        first.send("CREATE_MATCH", Map.of("nickname", "Alpha"));
        String code = first.receive("MATCH_CREATED").get("code").getAsString();
        second.send("JOIN_MATCH", Map.of("nickname", "Beta", "code", code.toLowerCase(java.util.Locale.ROOT)));
        assertEquals(code, second.receive("MATCH_JOINED").get("code").getAsString());
        first.receive("PLAYER_JOINED");
        return code;
    }

    private JsonObject[] start(Peer first, Peer second) throws IOException {
        join(first, second);
        first.send("SELECT_ANIMAL", Map.of("species", "MONO"));
        first.receive("ANIMAL_SELECTION", payload ->
            !payload.get("yourSpecies").isJsonNull() && payload.get("yourSpecies").getAsString().equals("MONO"));
        second.send("SELECT_ANIMAL", Map.of("species", "MARIPOSA"));
        return new JsonObject[]{first.receive("GAME_STARTED"), second.receive("GAME_STARTED")};
    }

    @Test
    void lobbyRequiresTwoDistinctSpeciesAndRejectsDuplicateMembership() throws IOException {
        Peer first = connect();
        Peer second = connect();
        String code = join(first, second);
        assertTrue(code.matches("[A-Z0-9]{4}"));
        first.send("CREATE_MATCH", Map.of("nickname", "Again"));
        assertTrue(first.receive("ERROR").get("message").getAsString().contains("Already"));
        first.send("SELECT_ANIMAL", Map.of("species", "MONO"));
        first.receive("ANIMAL_SELECTION", payload -> !payload.get("yourSpecies").isJsonNull());
        second.send("SELECT_ANIMAL", Map.of("species", "MONO"));
        assertTrue(second.receive("ERROR").get("message").getAsString().contains("already chose"));
        second.send("SELECT_ANIMAL", Map.of("species", "MARIPOSA"));
        JsonObject state = first.receive("GAME_STARTED");
        second.receive("GAME_STARTED");
        assertEquals(10, state.getAsJsonArray("ownAnimals").size());
        assertEquals("MONO", state.getAsJsonObject("ownPack").get("species").getAsString());
        assertEquals(1, state.getAsJsonArray("visibleZones").size());
        assertFalse(state.has("map"));
        assertFalse(state.has("allZones"));
        first.receive("STATE_UPDATE");
        Peer third = connect();
        third.send("JOIN_MATCH", Map.of("nickname", "Third", "code", code));
        assertTrue(third.receive("ERROR").get("message").getAsString().contains("full"));
    }

    @Test
    void actualMoveCommandsChangeOnlySelectedAnimalsAndValidateOtherCommands() throws IOException {
        Peer first = connect();
        Peer second = connect();
        JsonObject[] states = start(first, second);
        JsonObject state = states[0];
        JsonObject chosen = state.getAsJsonArray("ownAnimals").get(9).getAsJsonObject();
        JsonObject other = state.getAsJsonArray("ownAnimals").get(0).getAsJsonObject();
        JsonObject zone = state.getAsJsonObject("selectedZone");
        int chosenId = chosen.get("id").getAsInt();
        first.send("MOVE_ANIMALS", Map.of("animalIds", List.of(chosenId),
            "targetX", chosen.get("x").getAsInt() - 10, "targetY", chosen.get("y").getAsInt()));
        JsonObject moved = first.receive("STATE_UPDATE", payload -> {
            JsonObject current = payload.getAsJsonArray("ownAnimals").get(9).getAsJsonObject();
            return current.get("x").getAsInt() != chosen.get("x").getAsInt();
        });
        assertEquals(other.get("x"), moved.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().get("x"));
        first.send("ATTACK", Map.of("animalIds", List.of(chosenId), "targetId", other.get("id").getAsInt()));
        first.receive("ERROR");
        first.send("MOVE_ANIMALS", Map.of("animalIds",
            List.of(states[1].getAsJsonArray("ownAnimals").get(0).getAsJsonObject().get("id").getAsInt()),
            "targetX", zone.get("centerX").getAsInt(), "targetY", zone.get("centerY").getAsInt()));
        first.receive("ERROR");
        first.send("VIEW_ZONE", Map.of("zone", "not occupied"));
        first.receive("ERROR");
        first.send("VIEW_ZONE", Map.of("zone", zone.get("name").getAsString()));
        assertFalse(first.receive("STATE_UPDATE").has("map"));
        first.send("BOGUS", Map.of());
        first.receive("ERROR");
    }

    @Test
    void malformedJsonAndInvalidFieldTypesDoNotKillTheConnection() throws IOException {
        Peer peer = connect();
        for (String malformed : List.of("{", "[]", "null", "{\"type\":1,\"payload\":{}}",
            "{\"type\":\"CREATE_MATCH\",\"payload\":[]}", "{\"type\":\"CREATE_MATCH\",\"payload\":{}} trailing")) {
            peer.raw(malformed);
            peer.receive("ERROR");
        }
        peer.send("CREATE_MATCH", Map.of("nickname", "A \"quoted\" \u00e1 name"));
        peer.receive("MATCH_CREATED");
        peer.send("SELECT_ANIMAL", Map.of("species", "MARIPOSA"));
        assertTrue(peer.receive("ERROR").get("message").getAsString().contains("second"));
    }

    @Test
    void invalidIdsAndCoordinatesAreRejectedBeforeCoercion() {
        for (String value : List.of("\"2\"", "2.5", "2147483648", "1e400", "null", "[]")) {
            JsonObject payload = JsonParser.parseString("{\"targetX\":" + value + "}").getAsJsonObject();
            assertThrows(IllegalArgumentException.class, () -> ClientHandler.integer(payload, "targetX"));
        }
        JsonObject payload = JsonParser.parseString("{\"animalIds\":[1,2]}").getAsJsonObject();
        assertEquals(List.of(1, 2), ClientHandler.animalIds(payload));
        payload.getAsJsonArray("animalIds").add("3");
        assertThrows(IllegalArgumentException.class, () -> ClientHandler.animalIds(payload));
    }

    @Test
    void oversizedMessagesAreRejectedAndConnectionIsClosed() throws IOException {
        Peer peer = connect();
        peer.raw("x".repeat(8193));
        assertTrue(peer.receive("ERROR").get("message").getAsString().contains("large"));
        assertNull(peer.reader.readLine());
    }

    @Test
    void disconnectCancelsLobbyAndAllowsTheRemainingConnectionToCreateAgain() throws IOException {
        Peer first = connect();
        Peer second = connect();
        String oldCode = join(first, second);
        first.close();
        second.receive("MATCH_CANCELLED");
        second.send("CREATE_MATCH", Map.of("nickname", "New game"));
        second.receive("MATCH_CREATED");
        Peer third = connect();
        third.send("JOIN_MATCH", Map.of("nickname", "Third", "code", oldCode));
        assertTrue(third.receive("ERROR").get("message").getAsString().contains("not found"));
    }

    @Test
    void disconnectCancelsOnlyThatMatchWithoutInventingAWinner() throws IOException {
        Peer first = connect();
        Peer second = connect();
        Peer third = connect();
        Peer fourth = connect();
        start(first, second);
        JsonObject[] otherMatch = start(third, fourth);
        first.close();
        JsonObject result = second.receive("MATCH_CANCELLED");
        assertFalse(result.has("winner"));
        assertTrue(result.has("reason"));
        JsonObject state = third.receive("STATE_UPDATE");
        assertEquals(otherMatch[0].get("matchCode"), state.get("matchCode"));
        assertTrue(state.get("running").getAsBoolean());
    }

    @Test
    void shutdownClosesIdleConnectionsAndCanBeRepeated() throws IOException {
        Peer peer = connect();
        peer.send("CREATE_MATCH", Map.of("nickname", "Idle"));
        peer.receive("MATCH_CREATED");
        server.close();
        server.close();
        assertNull(peer.reader.readLine());
    }

    private static final class Peer implements AutoCloseable {
        private static final Gson JSON = new Gson();
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String type, Object payload) throws IOException {
            raw(JSON.toJson(Map.of("type", type, "payload", payload)));
        }

        void raw(String message) throws IOException {
            writer.write(message);
            writer.newLine();
            writer.flush();
        }

        JsonObject receive(String type) throws IOException {
            return receive(type, payload -> true);
        }

        JsonObject receive(String type, Predicate<JsonObject> predicate) throws IOException {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                String line = reader.readLine();
                assertNotNull(line, "Server closed connection while waiting for " + type);
                JsonObject message = JsonParser.parseString(line).getAsJsonObject();
                JsonObject payload = message.getAsJsonObject("payload");
                if (type.equals(message.get("type").getAsString()) && predicate.test(payload)) return payload;
            }
            throw new AssertionError("Timed out waiting for " + type);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
