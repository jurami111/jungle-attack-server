package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.entities.Animal;
import cr.ac.tec.jungleattack.server.entities.Bot;
import cr.ac.tec.jungleattack.server.entities.Pack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FogOfWar {

    public Map<String, Object> createSnapshot(Match match, Pack viewer, String requestedZone) {
        if (match == null || viewer == null
            || (viewer != match.getPlayer1Pack() && viewer != match.getPlayer2Pack())) {
            throw new IllegalArgumentException("Viewer must be one of the match's players");
        }

        Set<Zone> occupiedZones = findOccupiedZones(match.getMap(), viewer);
        Zone selectedZone = selectZone(occupiedZones, requestedZone);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("matchCode", match.getMatchCode());
        snapshot.put("running", match.isRunning());
        snapshot.put("ownPack", packState(viewer));
        snapshot.put("ownFlagColor", (viewer == match.getPlayer1Pack()
            ? match.getPlayer1Flag() : match.getPlayer2Flag()).getColor());
        snapshot.put("ownAnimals", animalStates(viewer, viewer.getMembers()));
        snapshot.put("visibleZones", zoneStates(occupiedZones));
        snapshot.put("selectedZone", selectedZone == null ? null : zoneState(selectedZone));
        snapshot.put("visibleAnimals", visibleAnimals(match, viewer, selectedZone));
        snapshot.put("visibleCoffeeCups", visibleCups(match, selectedZone));
        snapshot.put("visibleFlags", visibleFlags(match, selectedZone));
        return snapshot;
    }

    public boolean canViewZone(Match match, Pack viewer, String zoneName) {
        if (match == null || viewer == null || zoneName == null
            || (viewer != match.getPlayer1Pack() && viewer != match.getPlayer2Pack())) return false;
        for (Zone zone : findOccupiedZones(match.getMap(), viewer)) {
            if (zone.getName().equalsIgnoreCase(zoneName)) return true;
        }
        return false;
    }

    private Set<Zone> findOccupiedZones(MapGraph map, Pack pack) {
        Set<Zone> zones = new LinkedHashSet<>();
        for (Animal animal : pack.getMembers()) {
            if (animal.isAlive()) {
                Zone zone = map.getZoneByCoordinates(animal.getX(), animal.getY());
                if (zone != null) zones.add(zone);
            }
        }
        return zones;
    }

    private Zone selectZone(Set<Zone> occupiedZones, String requestedName) {
        if (requestedName != null) {
            for (Zone zone : occupiedZones) {
                if (zone.getName().equalsIgnoreCase(requestedName)) return zone;
            }
        }
        return occupiedZones.stream().findFirst().orElse(null);
    }

    private List<Map<String, Object>> visibleAnimals(Match match, Pack viewer, Zone selectedZone) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Pack pack : match.getPacks()) {
            if (pack == viewer) continue;
            for (Animal animal : pack.getMembers()) {
                if (animal.isAlive() && inZone(match, selectedZone, animal.getX(), animal.getY())) {
                    Map<String, Object> state = animalState(animal, pack);
                    state.put("bot", animal instanceof Bot);
                    visible.add(state);
                }
            }
        }
        return visible;
    }

    private List<Map<String, Object>> visibleCups(Match match, Zone selectedZone) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (CoffeeCup cup : match.getCoffeeCups()) {
            if (!cup.isConsumed() && inZone(match, selectedZone, cup.getX(), cup.getY())) {
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("x", cup.getX());
                state.put("y", cup.getY());
                visible.add(state);
            }
        }
        return visible;
    }

    private List<Map<String, Object>> visibleFlags(Match match, Zone selectedZone) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Flag flag : List.of(match.getPlayer1Flag(), match.getPlayer2Flag())) {
            if (flag.getZone() != selectedZone) continue;
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("owner", flag.getOwner().getSpecies().name());
            state.put("x", flag.getZone().getCenterX());
            state.put("y", flag.getZone().getCenterY());
            state.put("color", flag.getColor());
            state.put("captured", flag.isCaptured());
            visible.add(state);
        }
        return visible;
    }

    private boolean inZone(Match match, Zone selectedZone, int x, int y) {
        return selectedZone != null && match.getMap().getZoneByCoordinates(x, y) == selectedZone;
    }

    private List<Map<String, Object>> animalStates(Pack pack, List<Animal> animals) {
        List<Map<String, Object>> states = new ArrayList<>();
        for (Animal animal : animals) states.add(animalState(animal, pack));
        return states;
    }

    private Map<String, Object> animalState(Animal animal, Pack pack) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", animal.getId());
        state.put("species", pack.getSpecies().name());
        state.put("x", animal.getX());
        state.put("y", animal.getY());
        state.put("energy", animal.getEnergy());
        state.put("alive", animal.isAlive());
        return state;
    }

    private Map<String, Object> packState(Pack pack) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("species", pack.getSpecies().name());
        state.put("damageCapacity", pack.getDamageCapacity());
        state.put("botsKilled", pack.getBotsKilled());
        state.put("animalsEliminated", pack.getAnimalsEliminated());
        state.put("cupsConsumed", pack.getCupsConsumed());
        return state;
    }

    private List<Map<String, Object>> zoneStates(Set<Zone> zones) {
        List<Map<String, Object>> states = new ArrayList<>();
        for (Zone zone : zones) states.add(zoneState(zone));
        return states;
    }

    private Map<String, Object> zoneState(Zone zone) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", zone.getName());
        state.put("centerX", zone.getCenterX());
        state.put("centerY", zone.getCenterY());
        return state;
    }
}