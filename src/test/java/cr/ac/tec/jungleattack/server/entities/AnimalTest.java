package cr.ac.tec.jungleattack.server.entities;

import cr.ac.tec.jungleattack.server.game.MapCollision;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalTest {
    @Test
    void movementUsesRemainingDistanceAfterTurningAtAWaypoint() {
        MapCollision map = new MapCollision();
        map.addWalkableRect(0, 0, 100, 100);
        Animal animal = new PlayerAnimal(1, 10, 10);
        animal.followRoute(List.of(new int[]{12, 10}, new int[]{12, 20}));
        animal.updateMovement(1, map);
        assertEquals(12, animal.getX());
        assertEquals(13, animal.getY());
    }

    @Test
    void movementChecksEverySegmentInsteadOfCuttingACorner() {
        MapCollision map = new MapCollision();
        map.addWalkableRect(0, 0, 15, 40);
        map.addWalkableRect(10, 30, 50, 40);
        Animal animal = new PlayerAnimal(1, 10, 10);
        animal.followRoute(List.of(new int[]{10, 35}, new int[]{40, 35}));
        animal.updateMovement(11, map);
        assertEquals(40, animal.getX());
        assertEquals(35, animal.getY());
    }

    @Test
    void tenBotsCanDamageTenDifferentPlayers() {
        var players = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> new PlayerAnimal(i, i * 100, 0)).toList();
        for (int i = 0; i < 10; i++) {
            new Bot(10 + i, i * 100, 0).autoAttack(players, 1);
        }
        assertTrue(players.stream().allMatch(player -> player.getEnergy() == 19));
    }

    @Test
    void aBlockedAnimalWaitsAndResumesWithoutLosingItsRoute() {
        MapCollision map = new MapCollision();
        map.addWalkableRect(0, 0, 100, 100);
        Animal follower = new PlayerAnimal(1, 10, 10);
        Animal ahead = new PlayerAnimal(2, 20, 10);
        follower.moveTowards(40, 10);
        for (int tick = 0; tick < 60; tick++) {
            follower.updateMovement(1.0 / 30, map, List.of(follower, ahead));
        }
        assertTrue(follower.isMoving());
        assertTrue(follower.getX() <= 12);
        ahead.setY(40);
        follower.updateMovement(10, map, List.of(follower, ahead));
        assertEquals(40, follower.getX());
        assertFalse(follower.isMoving());
    }

    @Test
    void segmentValidationDoesNotSkipOnePixelGaps() {
        MapCollision map = new MapCollision();
        map.addWalkableRect(0, 0, 20, 10);
        map.addWalkableRect(0, 11, 20, 20);
        assertFalse(map.isSegmentWalkable(5, 9, 5, 12));
        assertFalse(map.isSegmentWalkable(5, 12, 5, 9));
        assertFalse(map.isSegmentWalkable(5, 9, 6, 12));
    }
}
