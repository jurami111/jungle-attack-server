package cr.ac.tec.jungleattack.server.game;

public class Zone {

    private final String name;
    private final int x;
    private final int y;

    public Zone (String name, int x, int y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public int getCenterX() {
        return x;
    }

    public int getCenterY() {
        return y;
    }

}