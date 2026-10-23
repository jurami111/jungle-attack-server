package cr.ac.tec.jungleattack.server.algorithms;

import cr.ac.tec.jungleattack.server.game.MapFactory;
import cr.ac.tec.jungleattack.server.game.MapGraph;
import cr.ac.tec.jungleattack.server.game.Zone;
import java.util.List;
import java.awt.Point;
import java.util.Collections;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PathFinderTest {
    @Test
    void routesExistBetweenAllTwelveZonesAndOnlyUseRegisteredEdges() {
        MapGraph map = MapFactory.createCostaRicaMap();
        for (Zone start : map.getAllZones()) {
            for (Zone end : map.getAllZones()) {
                List<Zone> route = PathFinder.findShortestPath(map, start, end);
                assertFalse(route.isEmpty());
                assertSame(start, route.get(0));
                assertSame(end, route.get(route.size() - 1));
                for (int i = 1; i < route.size(); i++) {
                    assertTrue(map.getConnectedZones(route.get(i - 1)).contains(route.get(i)));
                }
            }
        }
    }

    @Test
    void aStarChoosesTheCheaperRouteNotTheOneWithFewerHops() {
        MapGraph map = new MapGraph();
        Zone start = new Zone("start", 0, 0);
        Zone detour = new Zone("detour", 0, 100);
        Zone middle = new Zone("middle", 5, 0);
        Zone end = new Zone("end", 10, 0);
        for (Zone zone : List.of(start, detour, middle, end)) map.addZone(zone);
        map.addPath(start, detour);
        map.addPath(detour, end);
        map.addPath(start, middle);
        map.addPath(middle, end);
        assertEquals(List.of(start, middle, end), PathFinder.findShortestPath(map, start, end));
        Zone isolated = new Zone("isolated", 50, 50);
        map.addZone(isolated);
        assertTrue(PathFinder.findShortestPath(map, start, isolated).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PathFinder.findShortestPath(map, null, end));
    }

    @Test
    void aStarUsesThePolylineLengthInsteadOfTheDistanceBetweenCenters() {
        MapGraph map = new MapGraph();
        Zone start = new Zone("start", 0, 0);
        Zone middle = new Zone("middle", 5, 0);
        Zone end = new Zone("end", 10, 0);
        for (Zone zone : List.of(start, middle, end)) map.addZone(zone);
        map.addPath(start, end, List.of(new Point(0, 0), new Point(0, 100),
            new Point(10, 100), new Point(10, 0)));
        map.addPath(start, middle);
        map.addPath(middle, end);
        assertEquals(210, map.getPathLength(start, end));
        assertEquals(List.of(start, middle, end), PathFinder.findShortestPath(map, start, end));
        assertEquals(10, map.getPathLength(start, middle) + map.getPathLength(middle, end));
    }

    @Test
    void pathsAreReversibleDefensiveAndWalkableForEveryFormationSlot() {
        MapGraph map = MapFactory.createCostaRicaMap();
        for (Zone from : map.getAllZones()) {
            for (Zone to : map.getConnectedZones(from)) {
                List<Point> route = map.getPath(from, to);
                List<Point> reversed = new ArrayList<>(route);
                Collections.reverse(reversed);
                assertEquals(reversed, map.getPath(to, from));
                for (int slot = 0; slot < 10; slot++) {
                    int dx = (slot % 5 - 2) * 10;
                    int dy = slot / 5 * 10;
                    for (int i = 1; i < route.size(); i++) {
                        Point a = route.get(i - 1);
                        Point b = route.get(i);
                        assertTrue(map.getCollision().isSegmentWalkable(a.x + dx, a.y + dy, b.x + dx, b.y + dy),
                            from.getName() + " -> " + to.getName() + " slot " + slot);
                    }
                }
                route.get(0).setLocation(-100, -100);
                assertEquals(new Point(from.getCenterX(), from.getCenterY()), map.getPath(from, to).get(0));
            }
        }
    }
}
