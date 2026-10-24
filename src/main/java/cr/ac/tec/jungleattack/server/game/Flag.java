package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Animal;
import cr.ac.tec.jungleattack.server.entities.Pack;
import java.util.Objects;

public final class Flag {
    private final Zone zone;
    private final Pack owner;
    private final String color;
    private boolean captured;

    public Flag(Zone zone, Pack owner, String color) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.color = Objects.requireNonNull(color, "color");
    }

    public Zone getZone() {
        return zone;
    }

    public Pack getOwner() {
        return owner;
    }

    public String getColor() {
        return color;
    }

    public boolean isCaptured() {
        return captured;
    }

    public void capture() {
        captured = true;
    }

    public boolean isTouchedBy(Animal animal) {
        if (animal == null || !animal.isAlive()) {
            return false;
        }

        double distance = Math.hypot(
            animal.getX() - zone.getCenterX(),
            animal.getY() - zone.getCenterY()
        );
        return distance <= GameConstants.FLAG_RADIUS;
    }
}
