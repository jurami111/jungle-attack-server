package cr.ac.tec.jungleattack.server.entities;
import cr.ac.tec.jungleattack.server.config.GameConstants;
import java.util.List;

public class Bot extends Animal {
    private static final int INITIAL_ENERGY = 10;

    public Bot(int id, int x, int y) {
        super(id, INITIAL_ENERGY, x, y);
    }

    public void autoAttack(List<PlayerAnimal> enemies, int damage) {
        if (!isAlive() || isOnCooldown()) return;

        PlayerAnimal nearest = null;
        double nearestDistance = GameConstants.ENCOUNTER_RADIUS;
        for (PlayerAnimal target : enemies) {
            if (!target.isAlive()) continue;
            double distance = Math.hypot(this.x - target.getX(), this.y - target.getY());
            if (distance <= nearestDistance) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            nearest.recieveDamage(damage);
            startAttack(nearest);
        }
    }
}