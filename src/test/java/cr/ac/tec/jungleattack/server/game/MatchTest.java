package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Animal;
import cr.ac.tec.jungleattack.server.entities.Bot;
import cr.ac.tec.jungleattack.server.entities.Pack;
import cr.ac.tec.jungleattack.server.entities.PlayerAnimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchTest {
    private Match match() {
        Match match = new Match("TEST", new Random(7));
        match.start(AnimalSpecies.JAGUAR, AnimalSpecies.PUMA);
        return match;
    }

    @Test
    void onlyStartsOnceWithTwoDifferentSpecies() {
        Match match = new Match("TEST");
        match.updateTick(1);
        assertFalse(match.isRunning());
        assertThrows(IllegalArgumentException.class,
            () -> match.start(AnimalSpecies.PUMA, AnimalSpecies.PUMA));
        match.start(AnimalSpecies.JAGUAR, AnimalSpecies.PUMA);
        assertTrue(match.isRunning());
        assertEquals(7, match.getPacks().size());
        assertEquals(10, match.getPlayer1Pack().getMembers().size());
        assertEquals(20, match.getPlayer1Pack().getMembers().get(0).getEnergy());
        assertEquals(2, match.getPlayer1Pack().getDamageCapacity());
        for (Pack pack : match.getPacks().subList(2, 7)) {
            assertEquals(4, pack.getMembers().size());
            assertEquals(1, pack.getDamageCapacity());
            assertTrue(pack.getMembers().stream().allMatch(a -> a instanceof Bot && a.getEnergy() == 10));
        }
        assertThrows(IllegalStateException.class,
            () -> match.start(AnimalSpecies.JAGUAR, AnimalSpecies.PUMA));
    }

    @Test
    void randomStartingZonesAreDistinctAndRepeatable() {
        Match first = match();
        Match second = match();
        var zones = new HashSet<Zone>();
        for (int i = 0; i < first.getPacks().size(); i++) {
            Animal animal = first.getPacks().get(i).getMembers().get(0);
            zones.add(first.getMap().getZoneByCoordinates(animal.getX(), animal.getY()));
            assertEquals(animal.getX(), second.getPacks().get(i).getMembers().get(0).getX());
        }
        assertEquals(7, zones.size());
        assertFalse(zones.contains(null));
    }

    @Test
    void allZoneCentersAndThePoasTamarindoCorridorAreWalkable() {
        MapGraph map = MapFactory.createCostaRicaMap();
        for (Zone zone : map.getAllZones()) {
            assertTrue(map.isWalkable(zone.getCenterX(), zone.getCenterY()), zone.getName());
        }
        assertTrue(map.getCollision().isSegmentWalkable(1514, 1042, 1503, 1499));
        for (int y = 1200; y <= 1505; y++) {
            assertTrue(map.isWalkable(1480, y));
            assertTrue(map.isWalkable(1538, y));
        }
        assertFalse(map.isWalkable(1479, 1350));
        assertFalse(map.isWalkable(1539, 1350));
        assertFalse(map.isWalkable(1504, 859));
        assertFalse(map.isWalkable(2116, 865));
        assertFalse(map.isWalkable(1109, 1077));
    }

    @Test
    void movementOnlyUsesSelectedOwnedAnimalsAtConstantSpeed() {
        Match match = match();
        Pack pack = match.getPlayer1Pack();
        Animal chosen = pack.getMembers().get(4);
        Animal untouched = pack.getMembers().get(1);
        int x = chosen.getX();
        int otherX = untouched.getX();
        assertTrue(match.moveCommand(pack, List.of(chosen.getId()), x + 50, chosen.getY()));
        for (int tick = 0; tick < 30; tick++) match.updateTick(1.0 / 30);
        assertEquals(x + GameConstants.ANIMAL_SPEED, chosen.getX());
        assertEquals(otherX, untouched.getX());
        int foreignId = match.getPlayer2Pack().getMembers().get(0).getId();
        assertFalse(match.moveCommand(pack, List.of(chosen.getId(), foreignId), x, chosen.getY()));
        assertFalse(match.moveCommand(pack, List.of(chosen.getId()), -1, -1));
    }

    @Test
    void groupMovementPreservesDistinctFormationSlots() {
        Match match = match();
        Pack pack = match.getPlayer1Pack();
        Animal first = pack.getMembers().get(0);
        Animal second = pack.getMembers().get(1);
        Zone zone = match.getPlayer1Flag().getZone();
        assertTrue(match.moveCommand(pack, pack.getMembers().stream().map(Animal::getId).toList(),
            zone.getCenterX() + 60, zone.getCenterY()));
        match.updateMovement(12);
        assertEquals(GameConstants.FORMATION_SPACING, second.getX() - first.getX());
        assertNotEquals(first.getX(), second.getX());
    }

    @Test
    void aRejectedCommandDoesNotReplaceThePreviousValidatedRoutes() {
        Match match = match();
        Pack pack = match.getPlayer1Pack();
        Zone source = match.getPlayer1Flag().getZone();
        Zone destination = match.getMap().getConnectedZones(source).get(0);
        List<Integer> ids = pack.getMembers().stream().map(Animal::getId).toList();
        assertTrue(match.moveCommand(pack, ids, destination.getCenterX(), destination.getCenterY()));
        var targets = pack.getMembers().stream().map(Animal::getTargetPosition).toList();
        assertFalse(match.moveCommand(pack, ids, 1504, 859));
        assertEquals(targets, pack.getMembers().stream().map(Animal::getTargetPosition).toList());
        assertTrue(pack.getMembers().stream().allMatch(Animal::isMoving));
    }

    @Test
    void movementCannotCrossAnObstacleEvenWithALargeTick() {
        Match match = match();
        Animal animal = match.getPlayer1Pack().getMembers().get(0);
        animal.setX(1030);
        animal.setY(1077);
        assertFalse(match.moveCommand(match.getPlayer1Pack(), List.of(animal.getId()), 1200, 1077));
        animal.moveTowards(1200, 1077);
        match.updateTick(100);
        assertEquals(1033, animal.getX());
        assertTrue(match.getMap().isWalkable(animal.getX(), animal.getY()));
        match.updateTick(1);
        assertEquals(1033, animal.getX());
    }

    @Test
    void attacksRequireOrdersRespectCooldownRangeAndOwnership() {
        Match match = match();
        PlayerAnimal attacker = (PlayerAnimal) match.getPlayer1Pack().getMembers().get(0);
        Animal enemy = match.getPlayer2Pack().getMembers().get(0);
        attacker.setX(1000);
        attacker.setY(316);
        enemy.setX(attacker.getX() + GameConstants.ENCOUNTER_RADIUS);
        enemy.setY(attacker.getY());
        match.updateTick(0);
        assertEquals(20, enemy.getEnergy());
        assertTrue(match.attackCommand(match.getPlayer1Pack(), List.of(attacker.getId()), enemy.getId()));
        match.updateTick(0);
        assertEquals(18, enemy.getEnergy());
        match.updateTick(0.5);
        assertEquals(18, enemy.getEnergy());
        match.updateTick(0.5);
        assertEquals(16, enemy.getEnergy());
        enemy.setX(attacker.getX() + GameConstants.ENCOUNTER_RADIUS + 1);
        match.updateTick(1);
        enemy.setX(attacker.getX() + 10);
        match.updateTick(1);
        assertEquals(16, enemy.getEnergy(), "Leaving the range must cancel the attack order");
        assertFalse(match.attackCommand(match.getPlayer1Pack(), List.of(enemy.getId()), attacker.getId()));
        assertFalse(match.attackCommand(match.getPlayer1Pack(), List.of(attacker.getId()), attacker.getId()));
    }

    @Test
    void everyTwoBotKillsHealAllLivingPackMembersWithoutExceedingTwenty() {
        Match match = match();
        Pack pack = match.getPlayer1Pack();
        Animal attacker = pack.getMembers().get(0);
        Animal injured = pack.getMembers().get(1);
        Animal dead = pack.getMembers().get(2);
        injured.recieveDamage(10);
        dead.recieveDamage(20);
        for (Animal bot : match.getPacks().get(2).getMembers().subList(0, 2)) {
            bot.setX(attacker.getX());
            bot.setY(attacker.getY());
            bot.recieveDamage(8);
            assertTrue(match.attackCommand(pack, List.of(attacker.getId()), bot.getId()));
            match.updateTick(1);
        }
        assertEquals(2, pack.getBotsKilled());
        assertEquals(11, injured.getEnergy());
        assertEquals(0, dead.getEnergy());
        assertTrue(pack.getMembers().stream().allMatch(a -> a.getEnergy() <= 20));
        match.updateTick(1);
        assertEquals(2, pack.getBotsKilled(), "A dead bot must not be counted twice");
    }

    @Test
    void tenDistinctCoffeeCupsExistAtStartAndAreConsumedOnlyOnce() {
        Match match = match();
        assertEquals(10, match.getCoffeeCups().size());
        var positions = new HashSet<java.awt.Point>();
        for (CoffeeCup initial : match.getCoffeeCups()) {
            assertTrue(match.getMap().isWalkable(initial.getX(), initial.getY()));
            positions.add(new java.awt.Point(initial.getX(), initial.getY()));
        }
        assertEquals(10, positions.size());
        CoffeeCup cup = match.getCoffeeCups().get(0);
        Animal animal = match.getPlayer1Pack().getMembers().get(0);
        animal.setX(cup.getX());
        animal.setY(cup.getY());
        match.updateTick(0);
        match.updateTick(0);
        assertTrue(cup.isConsumed());
        assertEquals(3, match.getPlayer1Pack().getDamageCapacity());
        assertEquals(1, match.getPlayer1Pack().getCupsConsumed());
        for (int i = 0; i < 20; i++) match.updateTick(10);
        assertEquals(GameConstants.MAX_COFFEE_CUPS, match.getCoffeeCups().size());
    }

    @Test
    void deadAnimalsAndBotsCannotDrinkCoffeeOrAttack() {
        CoffeeCup cup = new CoffeeCup(0, 0);
        Bot bot = new Bot(1, 0, 0);
        PlayerAnimal player = new PlayerAnimal(2, 0, 0);
        assertFalse(cup.consumeBy(bot));
        player.recieveDamage(20);
        assertFalse(cup.consumeBy(player));
        bot.recieveDamage(10);
        PlayerAnimal target = new PlayerAnimal(3, 0, 0);
        bot.autoAttack(List.of(target), 1);
        assertEquals(20, target.getEnergy());
    }

    @Test
    void oneLivingAnimalCapturesFlagBeforeCombatAndEndsTheMatch() {
        Match match = match();
        Animal runner = match.getPlayer1Pack().getMembers().get(0);
        Flag flag = match.getPlayer2Flag();
        runner.recieveDamage(19);
        runner.setX(flag.getZone().getCenterX() + GameConstants.FLAG_RADIUS);
        runner.setY(flag.getZone().getCenterY());
        match.updateTick(0);
        assertTrue(flag.isCaptured());
        assertFalse(match.isRunning());
        assertSame(match.getPlayer1Pack(), match.getWinner());
        assertNotNull(match.getSummary());
        assertEquals(7, match.getSummary().getPacks().size());
        assertNotEquals(match.getPlayer1Flag().getColor(), flag.getColor());
        match.finishWithWinner(match.getPlayer2Pack());
        assertSame(match.getPlayer1Pack(), match.getWinner(), "Finishing twice must not overwrite the result");
    }

    @Test
    void totalEliminationStillEndsTheMatchAndCountsDeathsOnce() {
        Match match = match();
        for (Animal animal : match.getPlayer2Pack().getMembers()) animal.recieveDamage(20);
        match.updateTick(0);
        assertFalse(match.isRunning());
        assertSame(match.getPlayer1Pack(), match.getWinner());
        assertEquals(10, match.getSummary().getPacks().get(1).getAnimalsEliminated());
        match.updateTick(1);
        assertEquals(10, match.getPlayer2Pack().getAnimalsEliminated());
    }

    @Test
    void fogRevealsOnlyTheSelectedOccupiedZoneWithoutAnySightRadius() {
        Match match = match();
        Pack pack = match.getPlayer1Pack();
        Zone zone = match.getPlayer1Flag().getZone();
        for (Animal animal : pack.getMembers()) {
            animal.setX(zone.getCenterX());
            animal.setY(zone.getCenterY());
        }
        Animal enemy = match.getPlayer2Pack().getMembers().get(0);
        enemy.setX(zone.getCenterX() + 200);
        enemy.setY(zone.getCenterY());
        FogOfWar fog = new FogOfWar();
        Map<String, Object> state = fog.createSnapshot(match, pack, null);
        assertFalse(state.containsKey("map"));
        assertEquals(1, ((List<?>) state.get("visibleAnimals")).size());
        assertEquals(GameConstants.PLAYER1_FLAG_COLOR, state.get("ownFlagColor"));
        enemy.setX(match.getPlayer2Flag().getZone().getCenterX());
        enemy.setY(match.getPlayer2Flag().getZone().getCenterY());
        assertTrue(((List<?>) fog.createSnapshot(match, pack, null).get("visibleAnimals")).isEmpty());
        for (Animal animal : pack.getMembers()) animal.recieveDamage(20);
        assertFalse(fog.canViewZone(match, pack, zone.getName()));
        state = fog.createSnapshot(match, pack, null);
        assertNull(state.get("selectedZone"));
        assertTrue(((List<?>) state.get("visibleAnimals")).isEmpty());
        assertTrue(((List<?>) state.get("visibleFlags")).isEmpty());
    }

    @Test
    void visibilityFollowsCurrentOccupationAndCanSwitchBetweenZones() {
        Match match = match();
        Pack viewer = match.getPlayer1Pack();
        Zone first = match.getPlayer1Flag().getZone();
        Zone second = match.getPlayer2Flag().getZone();
        Animal scout = viewer.getMembers().get(0);
        scout.setX(second.getCenterX());
        scout.setY(second.getCenterY());
        FogOfWar fog = new FogOfWar();
        assertTrue(fog.canViewZone(match, viewer, first.getName()));
        assertTrue(fog.canViewZone(match, viewer, second.getName()));
        var snapshot = fog.createSnapshot(match, viewer, second.getName());
        assertEquals(2, ((List<?>) snapshot.get("visibleZones")).size());
        assertEquals(10, ((List<?>) snapshot.get("visibleAnimals")).size());
        assertEquals(1, ((List<?>) snapshot.get("visibleFlags")).size());
        scout.recieveDamage(20);
        assertFalse(fog.canViewZone(match, viewer, second.getName()));
        snapshot = fog.createSnapshot(match, viewer, second.getName());
        assertTrue(((List<?>) snapshot.get("visibleAnimals")).isEmpty());
    }

    @Test
    void summaryIncludesBotDeathsAndCancellationDoesNotDeclareAWinner() {
        Match match = match();
        Animal attacker = match.getPlayer1Pack().getMembers().get(0);
        Pack bots = match.getPacks().get(2);
        Animal victim = bots.getMembers().get(0);
        victim.setX(attacker.getX());
        victim.setY(attacker.getY());
        victim.recieveDamage(8);
        assertTrue(match.attackCommand(match.getPlayer1Pack(), List.of(attacker.getId()), victim.getId()));
        match.updateTick(0);
        match.finishWithWinner(match.getPlayer1Pack());
        assertEquals(7, match.getSummary().getPacks().size());
        assertEquals(1, match.getSummary().getPacks().get(2).getAnimalsEliminated());
        Match cancelled = match();
        cancelled.cancel();
        assertFalse(cancelled.isRunning());
        assertNull(cancelled.getWinner());
        assertNull(cancelled.getSummary());
    }
}
