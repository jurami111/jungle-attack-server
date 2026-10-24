package cr.ac.tec.jungleattack.server.game;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapCollision {
    private final List<Rectangle> walkableRects = new ArrayList<>();
    private final List<CircleObstacle> obstacleCircles = new ArrayList<>();

//----------------------------------------------------------------------------------
    // Estructura interna para los obstáculos circulares
    public static class CircleObstacle {
        int centerX;
        int centerY;
        int radius;

        public CircleObstacle(int centerX, int centerY, int radius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
        }

        //Usada para verificar si un punto está dentro del círculo de obstáculo
        public boolean contains(int x, int y) {
            double distance = Math.hypot(x - centerX, y - centerY);
            return distance <= radius;
        }
    }
//----------------------------------------------------------------------------------

    public void addWalkableRect(int x1, int y1, int x2, int y2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        //Agrega un camino a la lista de áreas caminables
        walkableRects.add(new Rectangle(minX, minY, maxX - minX, maxY - minY)); 
    }

    //Agrega un círculo de obstáculo a la lista de obstáculos
    public void addObstacleCircle(int cx, int cy, int radius) {
        obstacleCircles.add(new CircleObstacle(cx, cy, radius));
    }

    public boolean isWalkable(int x, int y) {
        //Aqui verificamos si el punto es un obstaculo / esta dentro de un obstaculo
        for (CircleObstacle circle : obstacleCircles) {
            if (circle.contains(x, y)) {
                return false; 
            }
        }

        //En caso de que no sea un obstaculo, revisamos si cae dentro de los caminos caminables
        for (Rectangle rect : walkableRects) {
            //Ese .contains es nativo de la clase rectangle de Java
            if (rect.contains(x, y)) {
                return true; 
            }
        }
        //En caso de que no haya caido en un camino caminable, retornamos false
        return false;
    }

    // Supercover: comprueba todas las celdas cruzadas, tambien al pasar por una esquina.
    public boolean isSegmentWalkable(int x1, int y1, int x2, int y2) {
        if (!isWalkable(x1, y1) || !isWalkable(x2, y2)) return false;
        long width = Math.abs((long) x2 - x1);
        long height = Math.abs((long) y2 - y1);
        int sx = Integer.compare(x2, x1);
        int sy = Integer.compare(y2, y1);
        long ix = 0;
        long iy = 0;
        while (ix < width || iy < height) {
            double decision = (1 + 2.0 * ix) * height - (1 + 2.0 * iy) * width;
            if (decision == 0) {
                if (!isWalkable(x1 + sx, y1) || !isWalkable(x1, y1 + sy)) return false;
                x1 += sx;
                y1 += sy;
                ix++;
                iy++;
            } else if (decision < 0) {
                x1 += sx;
                ix++;
            } else {
                y1 += sy;
                iy++;
            }
            if (!isWalkable(x1, y1)) return false;
        }
        return true;
    }

    public Point randomWalkablePoint(Random random) {
        for (int attempt = 0; attempt < 1000 && !walkableRects.isEmpty(); attempt++) {
            Rectangle area = walkableRects.get(random.nextInt(walkableRects.size()));
            int x = area.x + random.nextInt(Math.max(1, area.width));
            int y = area.y + random.nextInt(Math.max(1, area.height));
            if (isWalkable(x, y)) return new Point(x, y);
        }
        throw new IllegalStateException("Could not find a walkable map position");
    }

}