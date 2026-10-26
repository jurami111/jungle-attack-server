package cr.ac.tec.jungleattack.server.entities;

import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.game.MapCollision;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public abstract class Animal {
    protected int id;
    protected int energy;
    protected double x;
    protected double y;
    private Integer destinationX;
    private Integer destinationY;
    private final Deque<int[]> waypoints = new ArrayDeque<>();
    private double cooldownSeconds = 0;
    private double attackSeconds;
    private int attackSequence;
    private Point attackPosition;

    protected Animal(int id, int energy, int x, int y) {
        this.id = id;
        this.energy = energy;
        this.x = x;
        this.y = y;
    }

    public void moveTowards(int targetX, int targetY) {
        waypoints.clear();
        setDestination(targetX, targetY);
    }

    public void followRoute(List<int[]> route) {
        waypoints.clear();
        waypoints.addAll(route);
        nextWaypoint();
    }

    public void stop() {
        waypoints.clear();
        destinationX = null;
        destinationY = null;
    }

    public boolean isInRange(Animal other) {
        return Math.hypot(this.x - other.getX(), this.y - other.getY()) <= GameConstants.ENCOUNTER_RADIUS;
    }

    private void nextWaypoint() {
        int[] next = waypoints.poll();
        if (next == null) {
            destinationX = null;
            destinationY = null;
        } else {
            setDestination(next[0], next[1]);
        }
    }

    private void setDestination(int targetX, int targetY) {
        this.destinationX = targetX;
        this.destinationY = targetY;
    }

    public void updateMovement(double deltaTimeSeconds, MapCollision collision) {
        updateMovement(deltaTimeSeconds, collision, List.of());
    }

    public void updateMovement(double deltaTimeSeconds, MapCollision collision, List<Animal> teammates) {
        if (!isAlive()) return;
        double remaining = GameConstants.ANIMAL_SPEED * deltaTimeSeconds;
        while (destinationX != null && remaining > 0) {
            double dx = destinationX - x;
            double dy = destinationY - y;
            double distance = Math.hypot(dx, dy);
            if (distance == 0) {
                nextWaypoint();
                continue;
            }
            double step = Math.min(remaining, distance);
            double nextX = x + dx / distance * step;
            double nextY = y + dy / distance * step;
            if (!collision.isSegmentWalkable(getX(), getY(), (int) Math.round(nextX), (int) Math.round(nextY))) {
                return;
            }
            if (!isPositionFree(nextX, nextY, teammates)) return;
            x = nextX;
            y = nextY;
            remaining -= step;
            if (step == distance) nextWaypoint();
        }
    }

    private boolean isPositionFree(double nextX, double nextY, List<Animal> teammates) {
        for (Animal other : teammates) {
            if (other != this && other.isAlive()) {
                if (Line2D.ptSegDist(x, y, nextX, nextY, other.x, other.y) < GameConstants.MIN_ANIMAL_DISTANCE
                    || Math.hypot(Math.round(nextX) - other.getX(), Math.round(nextY) - other.getY())
                        < GameConstants.MIN_ANIMAL_DISTANCE) return false;
            }
        }
        return true;
    }

    public Point getTargetPosition() {
        int[] last = waypoints.peekLast();
        if (last != null) return new Point(last[0], last[1]);
        return destinationX == null ? new Point(getX(), getY()) : new Point(destinationX, destinationY);
    }

    public boolean isMoving() {
        return destinationX != null;
    }

    public void updateCooldown(double deltaTimeSeconds) {
        cooldownSeconds = Math.max(0, cooldownSeconds - deltaTimeSeconds);
        attackSeconds = Math.max(0, attackSeconds - deltaTimeSeconds);
    }

    public boolean isOnCooldown() {
        return cooldownSeconds > 0;
    }

    protected void startAttack(Animal target) {
        cooldownSeconds = GameConstants.ATTACK_COOLDOWN_MS / 1000.0;
        attackSeconds = GameConstants.ATTACK_ANIMATION_SECONDS;
        attackSequence++;
        attackPosition = new Point(target.getX(), target.getY());
    }

    public boolean isAttacking() { return isAlive() && attackSeconds > 0; }
    public int getAttackSequence() { return attackSequence; }
    public Point getAttackPosition() { return attackPosition == null ? null : new Point(attackPosition); }

    public void recieveDamage(int damage) {
        this.energy -= damage;
        if (this.energy < 0) {
            this.energy = 0;
        }
    }

    public int getEnergy() {
        return this.energy;
    }

    public void gainEnergy(int amount, int maxEnergy) {
        this.energy = Math.min(maxEnergy, this.energy + amount);
    }

    public boolean isAlive() {
        return this.energy > 0;
    }

    public int getId() { 
        return id; 
    }

    public int getX() { 
        return (int) Math.round(x); 
    }

    public int getY() { 
        return (int) Math.round(y); 
    }

    public Point2D.Double getPosition() { return new Point2D.Double(x, y); }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }
    
}