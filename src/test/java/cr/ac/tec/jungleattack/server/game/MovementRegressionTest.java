package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Animal;
import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class MovementRegressionTest {
    private Match matchAt(Zone zone) {
        Match match = new Match("TEST", new Random(7));
        match.start(AnimalSpecies.MONO, AnimalSpecies.MARIPOSA);
        List<Animal> animals = match.getPlayer1Pack().getMembers();
        for (int i = 0; i < animals.size(); i++) {
            animals.get(i).setX(zone.getCenterX() + (i % 5 - 2) * GameConstants.FORMATION_SPACING);
            animals.get(i).setY(zone.getCenterY() + i / 5 * GameConstants.FORMATION_SPACING);
        }
        return match;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tamarindo", "Volcan Arenal"})
    void allTenAnimalsActuallyReachTheZoneFromEveryOtherZoneAndRevealIt(String name) {
        MapGraph reference = MapFactory.createCostaRicaMap();
        Zone destination = reference.getAllZones().stream().filter(zone -> zone.getName().equals(name))
            .findFirst().orElseThrow();
        for (Zone origin : reference.getAllZones()) {
            if (origin == destination) continue;
            Match match = matchAt(origin);
            var pack = match.getPlayer1Pack();
            List<Animal> animals = pack.getMembers();
            assertTrue(match.moveCommand(pack, animals.stream().map(Animal::getId).toList(),
                destination.getCenterX(), destination.getCenterY()), origin.getName());
            List<Point> targets = animals.stream().map(Animal::getTargetPosition).toList();
            for (int tick = 0; tick < 4500 && animals.stream().anyMatch(Animal::isMoving); tick++) {
                match.updateMovement(1.0 / 30);
                for (int i = 0; i < animals.size(); i++) {
                    Animal animal = animals.get(i);
                    assertTrue(match.getMap().isWalkable(animal.getX(), animal.getY()), origin.getName());
                    for (int j = i + 1; j < animals.size(); j++) {
                        Animal other = animals.get(j);
                        assertTrue(Math.hypot(animal.getX() - other.getX(), animal.getY() - other.getY())
                            >= GameConstants.MIN_ANIMAL_DISTANCE, origin.getName());
                    }
                }
            }
            for (int i = 0; i < animals.size(); i++) {
                Animal animal = animals.get(i);
                assertFalse(animal.isMoving(), origin.getName() + " animal " + animal.getId());
                assertEquals(targets.get(i), new Point(animal.getX(), animal.getY()), origin.getName());
                assertEquals(name, match.getMap().getZoneByCoordinates(animal.getX(), animal.getY()).getName());
            }
            FogOfWar fog = new FogOfWar();
            assertTrue(fog.canViewZone(match, pack, name));
            Map<String, Object> snapshot = fog.createSnapshot(match, pack, name);
            assertEquals(List.of(Map.of("name", destination.getName(), "centerX", destination.getCenterX(),
                "centerY", destination.getCenterY())), snapshot.get("visibleZones"));
        }
    }

    @Test
    void aSingleExplorerLeavesStationaryTeammatesAndReachesTheClickedZoneWithoutFormationOffset() {
        Match match = matchAt(MapFactory.createCostaRicaMap().getAllZones().get(0));
        var pack = match.getPlayer1Pack();
        Animal scout = pack.getMembers().get(0);
        List<Point> teammates = pack.getMembers().stream().skip(1)
            .map(animal -> new Point(animal.getX(), animal.getY())).toList();
        assertTrue(match.moveCommand(pack, List.of(scout.getId()), 640, 327));
        assertEquals(new Point(640, 327), scout.getTargetPosition());
        for (int tick = 0; tick < 900 && scout.isMoving(); tick++) match.updateMovement(1.0 / 30);
        assertFalse(scout.isMoving());
        assertEquals(new Point(640, 327), new Point(scout.getX(), scout.getY()));
        assertEquals("Monteverde", match.getMap().getZoneByCoordinates(scout.getX(), scout.getY()).getName());
        assertEquals(teammates, pack.getMembers().stream().skip(1)
            .map(animal -> new Point(animal.getX(), animal.getY())).toList());
    }

    @Test
    void botsPatrolOnWalkableTerrainWithoutPlayerCommands() {
        Match match = new Match("TEST", new Random(7));
        match.start(AnimalSpecies.MONO, AnimalSpecies.MARIPOSA);
        List<Animal> bots = match.getPacks().subList(2, 7).stream()
            .flatMap(pack -> pack.getMembers().stream()).toList();
        List<Point> initial = bots.stream().map(animal -> new Point(animal.getX(), animal.getY())).toList();
        for (int tick = 0; tick < 120; tick++) {
            match.updateTick(1.0 / 30);
            for (Animal bot : bots) assertTrue(match.getMap().isWalkable(bot.getX(), bot.getY()));
        }
        for (int i = 0; i < bots.size(); i++) {
            assertNotEquals(initial.get(i), new Point(bots.get(i).getX(), bots.get(i).getY()));
        }
    }

    @Test
    void botStopsToAttackPublishesTheHitAndResumesPatrollingWhenThePlayerLeaves() {
        Match match = new Match("TEST", new Random(7));
        match.start(AnimalSpecies.MONO, AnimalSpecies.MARIPOSA);
        Animal player = match.getPlayer1Pack().getMembers().get(0);
        Animal bot = match.getPacks().get(2).getMembers().get(0);
        bot.setX(player.getX() - 25);
        bot.setY(player.getY());
        bot.moveTowards(player.getX() + 100, player.getY());
        Point initial = new Point(bot.getX(), bot.getY());
        match.updateTick(0);
        assertFalse(bot.isMoving());
        assertEquals(initial, new Point(bot.getX(), bot.getY()));
        assertEquals(19, player.getEnergy());
        var snapshot = new FogOfWar().createSnapshot(match, match.getPlayer1Pack(), null);
        List<?> visible = assertInstanceOf(List.class, snapshot.get("visibleAnimals"));
        boolean found = false;
        for (Object value : visible) {
            if (value instanceof Map<?, ?> state && state.get("id").equals(bot.getId())) {
                assertEquals(Map.of("sequence", 1, "targetX", player.getX(), "targetY", player.getY()),
                    state.get("attack"));
                found = true;
            }
        }
        assertTrue(found, "El bot que golpeo debe aparecer con su ataque en el snapshot");
        player.setX(player.getX() - 100);
        match.updateTick(1.0 / 30);
        assertTrue(bot.isMoving());
    }
}
