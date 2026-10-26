package cr.ac.tec.jungleattack.server.entities;

public class PlayerAnimal extends Animal {
    private static final int Initial_Energy = 20;

    public PlayerAnimal(int id, int x, int y) {
        super(id, Initial_Energy, x, y);
    }

    private Animal attackTarget;

    public boolean attack(Animal target, int damage) {
        if (!isAlive() || target == null || !target.isAlive() || isOnCooldown() || !isInRange(target)) return false;
        target.recieveDamage(damage);
        startAttack(target);
        return true;
    }

    public void setAttackTarget(Animal target) {
        this.attackTarget = target;
    }

    public Animal getAttackTarget() {
        return attackTarget;
    }

    public void clearAttackTarget() {
        this.attackTarget = null;
    }
}