package cr.ac.tec.jungleattack.server.algorithms;

import cr.ac.tec.jungleattack.server.game.MapCollision;
import cr.ac.tec.jungleattack.server.game.MapFactory;
import java.awt.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkablePathFinderTest {
    @Test
    void routesAroundThePoasCraterFromTheActualPositionWithinTheSameZone() {
        MapCollision collision = MapFactory.buildCostaRicaCollisions();
        Point start = new Point(1514, 690);
        Point target = new Point(1514, 1042);
        assertFalse(collision.isSegmentWalkable(start.x, start.y, target.x, target.y));
        var route = WalkablePathFinder.findPath(collision, start, target, List.of());
        assertTrue(route.size() > 1);
        Point previous = start;
        for (int[] point : route) {
            assertTrue(collision.isSegmentWalkable(previous.x, previous.y, point[0], point[1]));
            previous = new Point(point[0], point[1]);
        }
        assertEquals(target, previous);
    }

    @Test
    void rejectsBlockedEndpointsAndDisconnectedAreas() {
        MapCollision collision = new MapCollision();
        collision.addWalkableRect(0, 0, 40, 40);
        collision.addWalkableRect(100, 0, 140, 40);
        assertTrue(WalkablePathFinder.findPath(collision, new Point(10, 10), new Point(110, 10),
            List.of()).isEmpty());
        assertTrue(WalkablePathFinder.findPath(collision, new Point(10, 10), new Point(50, 10),
            List.of()).isEmpty());
    }
}
