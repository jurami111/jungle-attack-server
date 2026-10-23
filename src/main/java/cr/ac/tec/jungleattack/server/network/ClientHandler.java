package cr.ac.tec.jungleattack.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ClientHandler implements Runnable, AutoCloseable {
    private static final Gson JSON = new GsonBuilder().serializeNulls()
        .setStrictness(Strictness.STRICT).create();
    private final Socket socket;
    private final MatchManager matchManager;
    private final BufferedWriter writer;

    public ClientHandler(Socket socket, MatchManager matchManager) throws IOException {
        this.socket = socket;
        this.matchManager = matchManager;
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = readMessage(reader)) != null) {
                try {
                    JsonObject message = JSON.fromJson(line, JsonObject.class);
                    if (message == null || !message.has("payload") || !message.get("payload").isJsonObject()) {
                        throw new IllegalArgumentException("Expected type and object payload");
                    }
                    matchManager.handleMessage(this, string(message, "type"), message.getAsJsonObject("payload"));
                } catch (JsonParseException | IllegalArgumentException exception) {
                    sendError(exception.getMessage());
                }
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) System.err.println("Client connection error: " + exception.getMessage());
        } finally {
            matchManager.onDisconnect(this);
            close();
        }
    }

    private String readMessage(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1 && character != '\n') {
            if (line.length() >= GameConstants.MAX_NETWORK_MESSAGE_CHARS) {
                sendError("Message is too large");
                return null;
            }
            line.append((char) character);
        }
        return character == -1 && line.isEmpty() ? null : line.toString();
    }

    public boolean send(String type, Object payload) {
        try {
            synchronized (writer) {
                writer.write(JSON.toJson(new Message(type, payload)));
                writer.newLine();
                writer.flush();
            }
            return true;
        } catch (IOException exception) {
            if (!socket.isClosed()) System.err.println("Could not send " + type + ": " + exception.getMessage());
            close();
            matchManager.onDisconnect(this);
            return false;
        }
    }

    public void sendError(String message) {
        send("ERROR", java.util.Map.of("message", message));
    }

    static String string(JsonObject payload, String key) {
        JsonElement value = payload.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Expected a non-empty string: " + key);
        }
        return value.getAsString();
    }

    static int integer(JsonObject payload, String key) {
        return integer(payload.get(key), key);
    }

    private static int integer(JsonElement value, String key) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected an integer: " + key);
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a 32-bit integer: " + key);
        }
    }

    static List<Integer> animalIds(JsonObject payload) {
        if (!payload.has("animalIds") || !payload.get("animalIds").isJsonArray()
            || payload.getAsJsonArray("animalIds").isEmpty()) {
            throw new IllegalArgumentException("Expected a non-empty animalIds array");
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonElement id : payload.getAsJsonArray("animalIds")) {
            ids.add(integer(id, "animalIds"));
        }
        return ids;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException exception) {
            System.err.println("Could not close client socket: " + exception.getMessage());
        }
    }

    private record Message(String type, Object payload) {}
}
