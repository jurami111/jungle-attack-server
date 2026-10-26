import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Animal;
import cr.ac.tec.jungleattack.server.entities.Pack;
import cr.ac.tec.jungleattack.server.game.MapGraph;
import cr.ac.tec.jungleattack.server.game.Match;
import cr.ac.tec.jungleattack.server.game.Zone;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class PathProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/prueba-caminos.csv");
        Files.createDirectories(output.getParent());
        int total = 0;
        int complete = 0;
        int outside = 0;
        int overlaps = 0;
        int crossedBlocked = 0;
        int tooClose = 0;
        boolean individualOverlap;
        MapGraph reference = new Match("REFERENCE").getMap();
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(output))) {
            csv.println("tick_seconds,origin,destination,orders,accepted,arrived,total,ticks,outside,crossed_blocked,overlap_frames,min_distance,first_overlap,first_blocked_crossing");
            for (double delta : new double[]{1.0 / 30.0, 1.0}) {
                for (boolean individually : new boolean[]{false, true}) {
                    int batchComplete = 0;
                    for (Zone origin : reference.getAllZones()) {
                        for (Zone destination : reference.getAllZones()) {
                            if (origin == destination) continue;
                            Result result = run(origin.getName(), destination.getName(), delta, individually);
                            total++;
                            if (result.arrived == 10) { complete++; batchComplete++; }
                            else System.out.printf("FAILED %s -> %s (%s): accepted=%s, arrived=%d/10%n",
                                origin.getName(), destination.getName(), individually ? "individual" : "group",
                                result.accepted, result.arrived);
                            outside += result.outside;
                            crossedBlocked += result.crossedBlocked;
                            if (result.overlapFrames > 0) overlaps++;
                            if (result.minDistance < GameConstants.MIN_ANIMAL_DISTANCE) tooClose++;
                            result.write(csv);
                            csv.flush();
                        }
                    }
                    System.out.printf(Locale.ROOT, "BATCH delta=%.6f, %s: complete=%d/132%n",
                        delta, individually ? "individual" : "group", batchComplete);
                }
                System.out.printf(Locale.ROOT,
                    "CUMULATIVE delta=%.6f: complete=%d/%d, outside=%d, crossed_blocked=%d, scenarios_with_overlap=%d%n",
                    delta, complete, total, outside, crossedBlocked, overlaps);
            }
            Result individual = run("Playa Conchal", "Volcan Arenal", 1.0 / 30.0, true);
            individualOverlap = individual.overlapFrames > 0 || individual.arrived != 10
                || individual.outside != 0 || individual.crossedBlocked != 0;
            individual.write(csv);
            System.out.printf("INDIVIDUAL ORDERS TO THE SAME POINT: accepted=%s, arrived=%d/10, overlap_frames=%d, first=%s%n",
                individual.accepted, individual.arrived, individual.overlapFrames, individual.firstOverlap);
        }
        System.out.println("CSV: " + output.toAbsolutePath());
        System.out.println("Scope: real Match.moveCommand and Match.updateMovement; no combat or victory ticks.");
        System.out.println("Overlap means identical rounded center coordinates; sprite dimensions are not defined in the server.");
        if (complete != total || outside != 0 || crossedBlocked != 0 || overlaps != 0
            || tooClose != 0 || individualOverlap) {
            throw new AssertionError("FAILED: routes, non-overlap and walkability requirements are not satisfied; see CSV");
        }
    }

    private static Result run(String originName, String destinationName, double delta, boolean individually) {
        Match match = new Match("PROBE", new Random(7));
        match.start(AnimalSpecies.MONO, AnimalSpecies.MARIPOSA);
        MapGraph map = match.getMap();
        Zone origin = zone(map, originName);
        Zone destination = zone(map, destinationName);
        Pack pack = match.getPlayer1Pack();
        List<Animal> animals = pack.getMembers();
        List<Integer> ids = animals.stream().map(Animal::getId).toList();
        for (int i = 0; i < animals.size(); i++) {
            animals.get(i).setX(origin.getCenterX() + (i % 5 - 2) * GameConstants.FORMATION_SPACING);
            animals.get(i).setY(origin.getCenterY() + i / 5 * GameConstants.FORMATION_SPACING);
        }
        boolean accepted = true;
        if (individually) {
            for (int id : ids) {
                accepted &= match.moveCommand(pack, List.of(id), destination.getCenterX(), destination.getCenterY());
            }
        } else {
            accepted = match.moveCommand(pack, ids, destination.getCenterX(), destination.getCenterY());
        }
        List<java.awt.Point> targets = animals.stream().map(Animal::getTargetPosition).toList();
        Result result = new Result(originName, destinationName, delta, individually, accepted);
        inspect(result, map, animals, 0);
        int still = 0;
        int maximumTicks = (int) Math.ceil(180 / delta);
        for (int tick = 1; tick <= maximumTicks; tick++) {
            List<String> before = animals.stream().map(a -> a.getX() + "," + a.getY()).toList();
            int substeps = Math.max(1, (int) Math.ceil(GameConstants.ANIMAL_SPEED * delta));
            for (int step = 0; step < substeps; step++) {
                List<java.awt.Point> from = animals.stream().map(a -> new java.awt.Point(a.getX(), a.getY())).toList();
                match.updateMovement(delta / substeps);
                for (int index = 0; index < animals.size(); index++) {
                    Animal animal = animals.get(index);
                    int fromX = from.get(index).x;
                    int fromY = from.get(index).y;
                    if (!map.getCollision().isSegmentWalkable(fromX, fromY, animal.getX(), animal.getY())) {
                        result.crossedBlocked++;
                        if (result.firstBlockedCrossing.isEmpty()) {
                            result.firstBlockedCrossing = "tick=" + tick + " animal=" + animal.getId()
                                + " segment=" + fromX + ":" + fromY + "->" + animal.getX() + ":" + animal.getY();
                        }
                    }
                }
                inspect(result, map, animals, tick);
            }
            result.arrived = 0;
            for (int i = 0; i < animals.size(); i++) {
                Animal animal = animals.get(i);
                if (accepted && animal.getX() == targets.get(i).x && animal.getY() == targets.get(i).y
                    && !animal.isMoving()) result.arrived++;
            }
            result.ticks = tick;
            if (result.arrived == 10) break;
            boolean moved = false;
            for (int i = 0; i < animals.size(); i++) {
                if (!before.get(i).equals(animals.get(i).getX() + "," + animals.get(i).getY())) moved = true;
            }
            still = moved ? 0 : still + 1;
            if (still * delta >= 2) break;
        }
        return result;
    }

    private static void inspect(Result result, MapGraph map, List<Animal> animals, int tick) {
        Set<String> centers = new HashSet<>();
        boolean overlap = false;
        for (Animal animal : animals) {
            if (!map.isWalkable(animal.getX(), animal.getY())) result.outside++;
            String position = animal.getX() + "," + animal.getY();
            if (!centers.add(position)) {
                overlap = true;
                if (result.firstOverlap.isEmpty()) result.firstOverlap = "tick=" + tick + " at " + position;
            }
        }
        if (overlap) result.overlapFrames++;
        for (int i = 0; i < animals.size(); i++) {
            for (int j = i + 1; j < animals.size(); j++) {
                Animal first = animals.get(i);
                Animal second = animals.get(j);
                result.minDistance = Math.min(result.minDistance,
                    Math.hypot(first.getX() - second.getX(), first.getY() - second.getY()));
            }
        }
    }

    private static Zone zone(MapGraph map, String name) {
        return map.getAllZones().stream().filter(zone -> zone.getName().equals(name)).findFirst().orElseThrow();
    }

    private static final class Result {
        private final String origin;
        private final String destination;
        private final double delta;
        private final boolean individually;
        private final boolean accepted;
        private int arrived;
        private int ticks;
        private int outside;
        private int overlapFrames;
        private int crossedBlocked;
        private double minDistance = Double.POSITIVE_INFINITY;
        private String firstOverlap = "";
        private String firstBlockedCrossing = "";

        private Result(String origin, String destination, double delta, boolean individually, boolean accepted) {
            this.origin = origin;
            this.destination = destination;
            this.delta = delta;
            this.individually = individually;
            this.accepted = accepted;
        }

        private void write(PrintWriter output) {
            output.printf(Locale.ROOT, "%.6f,\"%s\",\"%s\",%s,%s,%d,10,%d,%d,%d,%d,%.3f,\"%s\",\"%s\"%n",
                delta, origin, destination, individually ? "individual" : "group", accepted,
                arrived, ticks, outside, crossedBlocked, overlapFrames, minDistance, firstOverlap, firstBlockedCrossing);
        }
    }
}
