package cr.ac.tec.jungleattack.server.network;

import com.google.gson.JsonObject;
import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Pack;
import cr.ac.tec.jungleattack.server.game.FogOfWar;
import cr.ac.tec.jungleattack.server.game.Match;
import cr.ac.tec.jungleattack.server.game.MatchSummary;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static cr.ac.tec.jungleattack.server.network.ClientHandler.animalIds;
import static cr.ac.tec.jungleattack.server.network.ClientHandler.integer;
import static cr.ac.tec.jungleattack.server.network.ClientHandler.string;

public final class MatchManager implements AutoCloseable {
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final ConcurrentHashMap<String, GameSession> matches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ClientHandler, GameSession> playerSessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final FogOfWar fog = new FogOfWar();
    private final ScheduledExecutorService gameLoop = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;

    public void handleMessage(ClientHandler client, String type, JsonObject payload) {
        if (closed) throw new IllegalArgumentException("Server is shutting down");
        String command = type.toUpperCase(Locale.ROOT);
        if (command.equals("CREATE_MATCH") || command.equals("JOIN_MATCH")) {
            enterLobby(client, payload, command.equals("CREATE_MATCH"));
            return;
        }
        GameSession session = playerSessions.get(client);
        if (session == null) throw new IllegalArgumentException("Create or join a match first");

        // El mismo candado protege comandos, ticks y desconexiones de esta partida.
        synchronized (session) {
            if (session.closed) throw new IllegalArgumentException("Match is closed");
            int player = session.clients[0] == client ? 0 : 1;
            if (command.equals("SELECT_ANIMAL")) {
                selectSpecies(session, player, string(payload, "species"));
                return;
            }
            if (session.match == null || !session.match.isRunning()) {
                throw new IllegalArgumentException("Match is not running");
            }
            Pack pack = session.pack(player);
            switch (command) {
                case "MOVE_ANIMALS" -> {
                    if (!session.match.moveCommand(pack, animalIds(payload),
                        integer(payload, "targetX"), integer(payload, "targetY"))) {
                        throw new IllegalArgumentException("Move command rejected");
                    }
                }
                case "ATTACK" -> {
                    if (!session.match.attackCommand(pack, animalIds(payload), integer(payload, "targetId"))) {
                        throw new IllegalArgumentException("Attack command rejected");
                    }
                }
                case "VIEW_ZONE" -> {
                    String zone = string(payload, "zone");
                    if (!fog.canViewZone(session.match, pack, zone)) {
                        throw new IllegalArgumentException("Zone is not currently visible");
                    }
                    session.viewedZones[player] = zone;
                    client.send("STATE_UPDATE", fog.createSnapshot(session.match, pack, zone));
                }
                default -> throw new IllegalArgumentException("Unsupported message type: " + type);
            }
        }
    }

    private void enterLobby(ClientHandler client, JsonObject payload, boolean create) {
        if (playerSessions.containsKey(client)) throw new IllegalArgumentException("Already in a match");
        String nickname = string(payload, "nickname").trim();
        if (nickname.length() > 24) throw new IllegalArgumentException("Nickname exceeds 24 characters");
        GameSession session;
        if (create) {
            do {
                StringBuilder code = new StringBuilder();
                for (int i = 0; i < 4; i++) code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
                session = new GameSession(code.toString(), client);
            } while (matches.putIfAbsent(session.code, session) != null);
        } else {
            session = matches.get(string(payload, "code").trim().toUpperCase(Locale.ROOT));
            if (session == null) throw new IllegalArgumentException("Match code not found");
        }
        synchronized (session) {
            if (session.closed || (!create && session.clients[1] != null)) {
                throw new IllegalArgumentException("Match is full or closed");
            }
            int player = create ? 0 : 1;
            session.clients[player] = client;
            playerSessions.put(client, session);
            if (!client.send(create ? "MATCH_CREATED" : "MATCH_JOINED", Map.of("code", session.code))) return;
            if (!create) {
                if (!session.clients[0].send("PLAYER_JOINED", Map.of("nickname", nickname))) return;
                sendSelection(session);
            }
        }
    }

    private void selectSpecies(GameSession session, int player, String name) {
        if (session.clients[1] == null) throw new IllegalArgumentException("Waiting for the second player");
        if (session.species[player] != null) throw new IllegalArgumentException("Species was already selected");
        AnimalSpecies species;
        try {
            species = AnimalSpecies.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown species: " + name);
        }
        if (session.species[1 - player] == species) {
            throw new IllegalArgumentException("The other player already chose that species");
        }
        session.species[player] = species;
        sendSelection(session);
        if (session.closed || session.species[0] == null || session.species[1] == null) return;
        session.match = new Match(session.code);
        session.match.start(session.species[0], session.species[1]);
        sendState(session, "GAME_STARTED");
        if (session.closed) return;
        session.lastTickNanos = System.nanoTime();
        session.tick = gameLoop.scheduleAtFixedRate(() -> update(session),
            GameConstants.GAME_TICK_MILLIS, GameConstants.GAME_TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void sendSelection(GameSession session) {
        List<String> available = new ArrayList<>();
        for (AnimalSpecies species : AnimalSpecies.values()) {
            if (species != session.species[0] && species != session.species[1]) available.add(species.name());
        }
        for (int i = 0; i < 2; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("availableSpecies", available);
            payload.put("yourSpecies", session.species[i]);
            payload.put("opponentSpecies", session.species[1 - i]);
            if (!session.clients[i].send("ANIMAL_SELECTION", payload)) return;
        }
    }

    private void update(GameSession session) {
        synchronized (session) {
            if (session.closed) return;
            long now = System.nanoTime();
            session.match.updateTick((now - session.lastTickNanos) / 1_000_000_000.0);
            session.lastTickNanos = now;
            sendState(session, "STATE_UPDATE");
            if (!session.closed && !session.match.isRunning()) {
                finish(session, "GAME_OVER", result(session.match));
            }
        }
    }

    private void sendState(GameSession session, String type) {
        for (int i = 0; i < 2; i++) {
            if (!session.clients[i].send(type,
                fog.createSnapshot(session.match, session.pack(i), session.viewedZones[i]))) return;
        }
    }

    private Map<String, Object> result(Match match) {
        MatchSummary summary = match.getSummary();
        return Map.of("winner", summary.getWinnerSpecies(), "durationSeconds", summary.getDurationSeconds(),
            "packs", summary.getPacks());
    }

    public void onDisconnect(ClientHandler client) {
        if (closed) return;
        GameSession session = playerSessions.get(client);
        if (session == null) return;
        synchronized (session) {
            if (session.closed) return;
            int disconnectedPlayer = session.clients[0] == client ? 0 : 1;
            session.clients[disconnectedPlayer] = null;
            playerSessions.remove(client, session);
            if (session.match != null) session.match.cancel();
            finish(session, "MATCH_CANCELLED", Map.of("reason", "The other player disconnected"));
        }
    }

    private void finish(GameSession session, String type, Object payload) {
        session.closed = true;
        if (session.tick != null) session.tick.cancel(false);
        matches.remove(session.code, session);
        for (ClientHandler client : session.clients) {
            if (client != null) playerSessions.remove(client, session);
        }
        for (ClientHandler client : session.clients) {
            if (client != null) client.send(type, payload);
        }
    }

    public int getActiveMatchCount() { return matches.size(); }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        gameLoop.shutdownNow();
        for (GameSession session : matches.values()) {
            synchronized (session) {
                session.closed = true;
                if (session.match != null) session.match.cancel();
            }
        }
        matches.clear();
        playerSessions.clear();
    }

    private static final class GameSession {
        private final String code;
        private final ClientHandler[] clients = new ClientHandler[2];
        private final AnimalSpecies[] species = new AnimalSpecies[2];
        private final String[] viewedZones = new String[2];
        private Match match;
        private ScheduledFuture<?> tick;
        private long lastTickNanos;
        private boolean closed;

        private GameSession(String code, ClientHandler creator) {
            this.code = code;
            clients[0] = creator;
        }

        private Pack pack(int player) {
            return player == 0 ? match.getPlayer1Pack() : match.getPlayer2Pack();
        }
    }
}
