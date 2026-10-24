package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.entities.Animal;
import cr.ac.tec.jungleattack.server.entities.PlayerAnimal;

public class CoffeeCup {
    private final int x;
    private final int y;
    private boolean consumed;

    public CoffeeCup(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isTouchedBy(Animal animal) {
        if (consumed || animal == null || !animal.isAlive()) return false;
        return Math.hypot(animal.getX() - x, animal.getY() - y) <= GameConstants.CUP_RADIUS;
    }

    public synchronized boolean consumeBy(Animal animal) {
        if (consumed || !(animal instanceof PlayerAnimal) || !isTouchedBy(animal)) return false;
        consumed = true;
        return true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
