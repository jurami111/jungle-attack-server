package cr.ac.tec.jungleattack.server.algorithms;

import cr.ac.tec.jungleattack.server.game.MapGraph;
import cr.ac.tec.jungleattack.server.game.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class PathFinder {
    private PathFinder() {}

    // A* elige el menor f = g + h: g es el costo recorrido y h estima lo que falta.
    public static List<Zone> findShortestPath(MapGraph map, Zone start, Zone end) {
        if (map == null || start == null || end == null
            || !map.getAllZones().contains(start) || !map.getAllZones().contains(end)) {
            throw new IllegalArgumentException("Both endpoints must belong to the map");
        }
        Map<Zone, Double> costs = new HashMap<>();
        Map<Zone, Zone> previous = new HashMap<>();
        Set<Zone> visited = new HashSet<>();
        PriorityQueue<Candidate> open = new PriorityQueue<>(Comparator.comparingDouble(Candidate::priority));
        costs.put(start, 0.0);
        open.add(new Candidate(start, distance(start, end)));
        while (!open.isEmpty()) {
            Zone current = open.remove().zone();
            if (!visited.add(current)) continue;
            if (current == end) return reconstruct(previous, end);
            for (Zone neighbor : map.getConnectedZones(current)) {
                if (visited.contains(neighbor)) continue;
                double cost = costs.get(current) + map.getPathLength(current, neighbor);
                if (cost < costs.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    costs.put(neighbor, cost);
                    previous.put(neighbor, current);
                    open.add(new Candidate(neighbor, cost + distance(neighbor, end)));
                }
            }
        }
        return List.of();
    }

    // La distancia recta no supera una ruta compuesta por esos mismos segmentos.
    private static double distance(Zone first, Zone second) {
        return Math.hypot(first.getCenterX() - second.getCenterX(), first.getCenterY() - second.getCenterY());
    }

    private static List<Zone> reconstruct(Map<Zone, Zone> previous, Zone end) {
        List<Zone> route = new ArrayList<>();
        for (Zone zone = end; zone != null; zone = previous.get(zone)) route.add(zone);
        Collections.reverse(route);
        return route;
    }

    private record Candidate(Zone zone, double priority) {}
}
