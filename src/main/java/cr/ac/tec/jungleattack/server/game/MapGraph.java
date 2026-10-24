package cr.ac.tec.jungleattack.server.game;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapGraph {
    private final Map<Zone, Map<Zone, List<Point>>> paths = new LinkedHashMap<>();
    private final MapCollision collision = MapFactory.buildCostaRicaCollisions();

    public void addZone(Zone zone) {
        paths.putIfAbsent(zone, new LinkedHashMap<>());
    }

    public void addPath(Zone from, Zone to) {
        addPath(from, to, List.of(
            new Point(from.getCenterX(), from.getCenterY()), new Point(to.getCenterX(), to.getCenterY())));
    }

    public void addPath(Zone from, Zone to, List<Point> points) {
        if (!paths.containsKey(from) || !paths.containsKey(to)) {
            throw new IllegalArgumentException("Both zones must be registered");
        }
        if (points.size() < 2
            || !points.get(0).equals(new Point(from.getCenterX(), from.getCenterY()))
            || !points.get(points.size() - 1).equals(new Point(to.getCenterX(), to.getCenterY()))) {
            throw new IllegalArgumentException("A path must start and end at its zone centers");
        }
        List<Point> forward = copyPoints(points);
        List<Point> backward = new ArrayList<>(forward);
        Collections.reverse(backward);
        paths.get(from).put(to, forward);
        paths.get(to).put(from, List.copyOf(backward));
    }

    public List<Zone> getConnectedZones(Zone zone) {
        if (!paths.containsKey(zone)) throw new IllegalArgumentException("Unknown zone");
        return List.copyOf(paths.get(zone).keySet());
    }

    public List<Point> getPath(Zone from, Zone to) {
        if (!paths.containsKey(from) || !paths.get(from).containsKey(to)) {
            throw new IllegalArgumentException("Zones are not connected");
        }
        return copyPoints(paths.get(from).get(to));
    }

    public double getPathLength(Zone from, Zone to) {
        List<Point> points = getPath(from, to);
        double length = 0;
        for (int i = 1; i < points.size(); i++) length += points.get(i - 1).distance(points.get(i));
        return length;
    }

    private List<Point> copyPoints(List<Point> points) {
        return points.stream().map(Point::new).toList();
    }

    public MapCollision getCollision() { return collision; }

    // La misma particion espacial se usa para movimiento y visibilidad.
    public Zone getZoneByCoordinates(int x, int y) {
        Zone nearest = null;
        double shortest = Double.POSITIVE_INFINITY;
        for (Zone zone : paths.keySet()) {
            double distance = Math.hypot(x - zone.getCenterX(), y - zone.getCenterY());
            if (distance < shortest) {
                shortest = distance;
                nearest = zone;
            }
        }
        return nearest;
    }

    public boolean isWalkable(int x, int y) { return collision.isWalkable(x, y); }
    public List<Zone> getAllZones() { return List.copyOf(paths.keySet()); }
}
