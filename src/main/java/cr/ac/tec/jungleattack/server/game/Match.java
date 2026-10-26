package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.algorithms.PathFinder;
import cr.ac.tec.jungleattack.server.algorithms.WalkablePathFinder;
import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Animal;
import cr.ac.tec.jungleattack.server.entities.Bot;
import cr.ac.tec.jungleattack.server.entities.Pack;
import cr.ac.tec.jungleattack.server.entities.PlayerAnimal;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class Match {
    private final String matchCode;
    private final MapGraph map = MapFactory.createCostaRicaMap();
    private final List<Pack> packs = new ArrayList<>();
    private final List<CoffeeCup> coffeeCups = new ArrayList<>();
    private final Set<Animal> reportedDeaths = new HashSet<>();
    private final Map<Animal, Double> blockedSeconds = new LinkedHashMap<>();
    private final Map<Pack, Zone> patrolDestinations = new LinkedHashMap<>();
    private final Map<Pack, Double> patrolRetrySeconds = new LinkedHashMap<>();
    private final Random random;
    private Flag player1Flag;
    private Flag player2Flag;
    private Pack winner;
    private boolean running;
    private int nextAnimalId = 1;
    private long startedAtMillis;
    private MatchSummary summary;

    public Match(String matchCode) {
        this(matchCode, new Random());
    }

    public Match(String matchCode, Random random) {
        this.matchCode = Objects.requireNonNull(matchCode, "matchCode");
        this.random = Objects.requireNonNull(random, "random");
    }

    // MatchManager inicia la partida solo cuando ambos jugadores eligieron especie.
    public void start(AnimalSpecies firstSpecies, AnimalSpecies secondSpecies) {
        if (!packs.isEmpty()) throw new IllegalStateException("Match has already started");
        if (firstSpecies == null || secondSpecies == null || firstSpecies == secondSpecies) {
            throw new IllegalArgumentException("Players must choose two different species");
        }
        List<Zone> zones = new ArrayList<>(map.getAllZones());
        Collections.shuffle(zones, random);
        packs.add(createPack(firstSpecies, zones.get(0), true));
        packs.add(createPack(secondSpecies, zones.get(1), true));
        player1Flag = new Flag(zones.get(0), getPlayer1Pack(), GameConstants.PLAYER1_FLAG_COLOR);
        player2Flag = new Flag(zones.get(1), getPlayer2Pack(), GameConstants.PLAYER2_FLAG_COLOR);
        for (AnimalSpecies species : AnimalSpecies.values()) {
            if (species != firstSpecies && species != secondSpecies) {
                packs.add(createPack(species, zones.get(packs.size()), false));
            }
        }
        Set<Point> positions = new HashSet<>();
        while (positions.size() < GameConstants.MAX_COFFEE_CUPS) {
            Point point = map.getCollision().randomWalkablePoint(random);
            if (positions.add(point)) coffeeCups.add(new CoffeeCup(point.x, point.y));
        }
        startedAtMillis = System.currentTimeMillis();
        running = true;
    }

    private Pack createPack(AnimalSpecies species, Zone zone, boolean player) {
        Pack pack = new Pack(species, player ? 2 : 1);
        int count = player ? 10 : 4;
        for (int i = 0; i < count; i++) {
            int x = zone.getCenterX() + (i % 5 - 2) * GameConstants.FORMATION_SPACING;
            int y = zone.getCenterY() + (i / 5) * GameConstants.FORMATION_SPACING;
            pack.addMember(player ? new PlayerAnimal(nextAnimalId++, x, y) : new Bot(nextAnimalId++, x, y));
        }
        return pack;
    }

    // El servidor proporciona el tiempo transcurrido; Match no crea hilos ni temporizadores.
    public void updateTick(double deltaTimeSeconds) {
        if (!Double.isFinite(deltaTimeSeconds) || deltaTimeSeconds < 0) {
            throw new IllegalArgumentException("Tick duration must be finite and non-negative");
        }
        if (!running) return;
        for (Pack pack : packs) {
            for (Animal animal : pack.getMembers()) {
                animal.updateCooldown(deltaTimeSeconds);
            }
        }
        updateBotPatrols(deltaTimeSeconds);
        updateMovement(deltaTimeSeconds);
        if (checkVictory()) return;
        collectCoffeeCups();
        processPlayerAttacks();
        List<PlayerAnimal> players = new ArrayList<>();
        for (Pack pack : playerPacks()) {
            for (Animal animal : pack.getMembers()) {
                if (animal instanceof PlayerAnimal player && player.isAlive()) players.add(player);
            }
        }
        for (Pack pack : packs) {
            for (Animal animal : pack.getMembers()) {
                if (animal instanceof Bot bot) bot.autoAttack(players, pack.getDamageCapacity());
            }
        }
        reportNewDeaths();
        checkVictory();
    }

    private void updateBotPatrols(double seconds) {
        List<Animal> players = playerPacks().stream().flatMap(pack -> pack.getMembers().stream())
            .filter(Animal::isAlive).toList();
        for (Pack pack : packs) {
            if (isPlayerPack(pack)) continue;
            List<Animal> ready = new ArrayList<>();
            for (Animal bot : pack.getMembers()) {
                if (!bot.isAlive()) continue;
                if (players.stream().anyMatch(bot::isInRange)) {
                    bot.stop();
                } else if (!bot.isMoving()) {
                    ready.add(bot);
                }
            }
            if (ready.isEmpty()) continue;
            double retry = Math.max(0, patrolRetrySeconds.getOrDefault(pack, 0.0) - seconds);
            patrolRetrySeconds.put(pack, retry);
            if (retry > 0) continue;
            Zone destination = patrolDestinations.get(pack);
            if (destination == null || pack.getMembers().stream().noneMatch(Animal::isMoving)) {
                Zone origin = map.getZoneByCoordinates(ready.get(0).getX(), ready.get(0).getY());
                List<Zone> neighbors = map.getConnectedZones(origin);
                destination = neighbors.get(random.nextInt(neighbors.size()));
                patrolDestinations.put(pack, destination);
            }
            if (!planMovement(pack, ready, destination.getCenterX(), destination.getCenterY())) {
                System.err.println("Bot patrol route unavailable: " + pack.getSpecies() + " -> " + destination.getName());
                patrolRetrySeconds.put(pack, GameConstants.BOT_PATROL_RETRY_SECONDS);
                patrolDestinations.remove(pack);
            }
        }
    }

    // Pasos pequenos para que un tick retrasado no salte por encima de otro animal.
    public void updateMovement(double deltaTimeSeconds) {
        if (!Double.isFinite(deltaTimeSeconds) || deltaTimeSeconds < 0) {
            throw new IllegalArgumentException("Movement duration must be finite and non-negative");
        }
        if (!running) return;
        int steps = Math.max(1, (int) Math.ceil(GameConstants.ANIMAL_SPEED * deltaTimeSeconds));
        for (int i = 0; i < steps; i++) {
            for (Pack pack : packs) {
                List<Animal> teammates = pack.getMembers();
                for (Animal animal : teammates) {
                    int beforeX = animal.getX();
                    int beforeY = animal.getY();
                    animal.updateMovement(deltaTimeSeconds / steps, map.getCollision(), teammates);
                    if (animal.isAlive() && animal.isMoving()
                        && animal.getX() == beforeX && animal.getY() == beforeY) {
                        double blocked = blockedSeconds.getOrDefault(animal, 0.0) + deltaTimeSeconds / steps;
                        if (blocked >= GameConstants.MOVEMENT_REPLAN_SECONDS) {
                            var occupied = teammates.stream().filter(other -> other != animal && other.isAlive())
                                .map(Animal::getPosition).toList();
                            List<int[]> route = WalkablePathFinder.findPath(map.getCollision(),
                                animal.getPosition(), animal.getTargetPosition(), occupied);
                            if (!route.isEmpty()) animal.followRoute(route);
                            blocked = 0;
                        }
                        blockedSeconds.put(animal, blocked);
                    } else {
                        blockedSeconds.remove(animal);
                    }
                }
            }
        }
    }

    public boolean moveCommand(Pack player, List<Integer> ids, int x, int y) {
        if (!running || !map.isWalkable(x, y)) return false;
        List<Animal> selected = selectAnimals(player, ids);
        if (selected.isEmpty()) return false;
        return planMovement(player, selected, x, y);
    }

    private boolean planMovement(Pack player, List<Animal> selected, int x, int y) {
        Map<Animal, List<int[]>> routes = new LinkedHashMap<>();
        List<Point> reserved = new ArrayList<>();
        for (Animal member : player.getMembers()) {
            if (member.isAlive() && !selected.contains(member)) reserved.add(member.getTargetPosition());
        }
        Zone destination = map.getZoneByCoordinates(x, y);
        boolean crossingZones = selected.stream().anyMatch(animal ->
            map.getZoneByCoordinates(animal.getX(), animal.getY()) != destination);
        int spacing = selected.size() == 1 || crossingZones ? GameConstants.FORMATION_SPACING * 2
            : GameConstants.FORMATION_SPACING;
        for (int index = 0; index < selected.size(); index++) {
            Animal animal = selected.get(index);
            int dx = selected.size() == 1 ? 0 : (index % 5 - 2) * GameConstants.FORMATION_SPACING;
            int dy = index / 5 * GameConstants.FORMATION_SPACING;
            Point target = findFreeDestination(x + dx, y + dy, reserved, destination, spacing);
            if (target == null) return false;
            List<int[]> route = buildRoute(animal, target, dx, dy);
            if (route.isEmpty()) return false;
            reserved.add(target);
            routes.put(animal, route);
        }
        routes.forEach((animal, route) -> {
            animal.followRoute(route);
            blockedSeconds.remove(animal);
        });
        return true;
    }

    private Point findFreeDestination(int x, int y, List<Point> reserved, Zone destination, int spacing) {
        for (int radius = 0; radius <= GameConstants.FORMATION_SPACING * 12;
            radius += GameConstants.FORMATION_SPACING) {
            for (int dy = -radius; dy <= radius; dy += GameConstants.FORMATION_SPACING) {
                for (int dx = -radius; dx <= radius; dx += GameConstants.FORMATION_SPACING) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    Point candidate = new Point(x + dx, y + dy);
                    if (map.isWalkable(candidate.x, candidate.y)
                        && map.getZoneByCoordinates(candidate.x, candidate.y) == destination
                        && reserved.stream().noneMatch(p -> p.distance(candidate) < spacing)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private List<int[]> buildRoute(Animal animal, Point target, int dx, int dy) {
        if (map.getCollision().isSegmentWalkable(animal.getX(), animal.getY(), target.x, target.y)) {
            return List.of(new int[]{target.x, target.y});
        }
        List<int[]> points = new ArrayList<>();
        Zone origin = map.getZoneByCoordinates(animal.getX(), animal.getY());
        Zone destination = map.getZoneByCoordinates(target.x, target.y);
        if (origin != destination) {
            List<Zone> zones = PathFinder.findShortestPath(map, origin, destination);
            if (zones.isEmpty()) return List.of();
            for (int i = 1; i < zones.size(); i++) {
                for (Point point : map.getPath(zones.get(i - 1), zones.get(i))) {
                    points.add(new int[]{point.x + dx, point.y + dy});
                }
            }
        }
        points.add(new int[]{target.x, target.y});
        int fromX = animal.getX();
        int fromY = animal.getY();
        for (int[] point : points) {
            if (!map.getCollision().isSegmentWalkable(fromX, fromY, point[0], point[1])) {
                return WalkablePathFinder.findPath(map.getCollision(),
                    new Point(animal.getX(), animal.getY()), target, List.of());
            }
            fromX = point[0];
            fromY = point[1];
        }
        return points;
    }

    public boolean attackCommand(Pack player, List<Integer> ids, int targetId) {
        if (!running) return false;
        Animal target = null;
        for (Pack pack : packs) {
            if (pack == player) continue;
            for (Animal animal : pack.getMembers()) {
                if (animal.getId() == targetId && animal.isAlive()) target = animal;
            }
        }
        if (target == null) return false;
        boolean accepted = false;
        for (Animal animal : selectAnimals(player, ids)) {
            if (animal instanceof PlayerAnimal attacker && attacker.isInRange(target)) {
                attacker.setAttackTarget(target);
                accepted = true;
            }
        }
        return accepted;
    }

    private List<Animal> selectAnimals(Pack player, List<Integer> ids) {
        if (!isPlayerPack(player) || ids == null || ids.isEmpty()) return List.of();
        Set<Integer> remaining = new HashSet<>(ids);
        List<Animal> selected = new ArrayList<>();
        for (Animal animal : player.getMembers()) {
            if (animal.isAlive() && remaining.remove(animal.getId())) selected.add(animal);
        }
        return remaining.isEmpty() ? selected : List.of();
    }

    private void processPlayerAttacks() {
        for (Pack pack : playerPacks()) {
            for (Animal animal : pack.getMembers()) {
                PlayerAnimal attacker = (PlayerAnimal) animal;
                Animal target = attacker.getAttackTarget();
                if (target == null) continue;
                if (!attacker.isAlive() || !target.isAlive() || !attacker.isInRange(target)) {
                    attacker.clearAttackTarget();
                } else if (attacker.attack(target, pack.getDamageCapacity())
                    && !target.isAlive() && target instanceof Bot) {
                    if (pack.registerBotKill() % GameConstants.BOTS_PER_ENERGY_BONUS == 0) {
                        for (Animal member : pack.getMembers()) {
                            if (member.isAlive()) {
                                member.gainEnergy(GameConstants.ENERGY_BONUS, GameConstants.MAX_PLAYER_ENERGY);
                            }
                        }
                    }
                }
            }
        }
    }

    private void collectCoffeeCups() {
        for (CoffeeCup cup : coffeeCups) {
            for (Pack pack : playerPacks()) {
                for (Animal animal : pack.getMembers()) {
                    if (cup.consumeBy(animal)) {
                        pack.setDamageCapacity(GameConstants.CUP_DAMAGE_BONUS);
                        pack.registerCupConsumed();
                    }
                }
            }
        }
    }

    private boolean checkVictory() {
        for (Flag flag : List.of(player2Flag, player1Flag)) {
            Pack opponent = flag.getOwner() == getPlayer1Pack() ? getPlayer2Pack() : getPlayer1Pack();
            if (opponent.getMembers().stream().anyMatch(flag::isTouchedBy)) {
                flag.capture();
                finishWithWinner(opponent);
                return true;
            }
        }
        for (Flag flag : List.of(player1Flag, player2Flag)) {
            if (flag.getOwner().getMembers().stream().noneMatch(Animal::isAlive)) {
                Pack opponent = flag.getOwner() == getPlayer1Pack() ? getPlayer2Pack() : getPlayer1Pack();
                finishWithWinner(opponent);
                return true;
            }
        }
        return false;
    }

    private void reportNewDeaths() {
        for (Pack pack : packs) {
            for (Animal animal : pack.getMembers()) {
                if (!animal.isAlive() && reportedDeaths.add(animal)) pack.registerAnimalEliminated();
            }
        }
    }

    public void finishWithWinner(Pack winningPack) {
        if (!isPlayerPack(winningPack)) throw new IllegalArgumentException("Winner must be a player pack");
        if (!running) return;
        reportNewDeaths();
        winner = winningPack;
        running = false;
        List<MatchSummary.PackSummary> results = new ArrayList<>();
        for (Pack pack : packs) {
            results.add(new MatchSummary.PackSummary(
                pack.getSpecies(), pack.getBotsKilled(), pack.getAnimalsEliminated(), pack.getCupsConsumed()));
        }
        summary = new MatchSummary(winner.getSpecies(),
            (System.currentTimeMillis() - startedAtMillis) / 1000, results);
    }

    public void cancel() {
        running = false;
    }

    private List<Pack> playerPacks() { return List.of(getPlayer1Pack(), getPlayer2Pack()); }
    private boolean isPlayerPack(Pack pack) { return packs.size() >= 2 && playerPacks().contains(pack); }
    public String getMatchCode() { return matchCode; }
    public MapGraph getMap() { return map; }
    public List<Pack> getPacks() { return List.copyOf(packs); }
    public Pack getPlayer1Pack() { return packs.get(0); }
    public Pack getPlayer2Pack() { return packs.get(1); }
    public Flag getPlayer1Flag() { return player1Flag; }
    public Flag getPlayer2Flag() { return player2Flag; }
    public List<CoffeeCup> getCoffeeCups() { return List.copyOf(coffeeCups); }
    public Pack getWinner() { return winner; }
    public boolean isRunning() { return running; }
    public MatchSummary getSummary() { return summary; }
}
