package cr.ac.tec.jungleattack.server.algorithms;

import cr.ac.tec.jungleattack.server.config.GameConstants;
import cr.ac.tec.jungleattack.server.game.MapCollision;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class WalkablePathFinder {
    private static final int STEP = 8;
    private static final double CLEARANCE = GameConstants.MIN_ANIMAL_DISTANCE + 2;

    private WalkablePathFinder() {}

    public static List<int[]> findPath(MapCollision map, Point2D start, Point target,
            List<? extends Point2D> occupied) {
        Point anchor = rounded(start);
        if (!map.isWalkable(anchor.x, anchor.y) || !map.isWalkable(target.x, target.y)) return List.of();
        if (occupied.stream().anyMatch(point -> point.distance(target) < CLEARANCE)) return List.of();
        if (clear(map, start, target, occupied)) return List.of(new int[]{target.x, target.y});
        List<int[]> route = search(map, start, target, occupied, STEP);
        return route.isEmpty() ? search(map, start, target, occupied, STEP / 2) : route;
    }

    private static List<int[]> search(MapCollision map, Point2D start, Point target,
            List<? extends Point2D> occupied, int step) {
        Point anchor = rounded(start);
        Rectangle bounds = map.getWalkableBounds();
        int minX = Math.floorDiv(bounds.x - anchor.x, step);
        int minY = Math.floorDiv(bounds.y - anchor.y, step);
        int columns = Math.floorDiv(bounds.x + bounds.width - anchor.x, step) - minX + 1;
        int rows = Math.floorDiv(bounds.y + bounds.height - anchor.y, step) - minY + 1;
        int size = columns * rows;
        double[] costs = new double[size];
        Arrays.fill(costs, Double.POSITIVE_INFINITY);
        int[] previous = new int[size];
        Arrays.fill(previous, -1);
        boolean[] closed = new boolean[size];
        int origin = -minY * columns - minX;
        costs[origin] = 0;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::priority));
        open.add(new Node(origin, start.distance(target)));
        while (!open.isEmpty()) {
            int index = open.remove().index();
            if (closed[index]) continue;
            closed[index] = true;
            Point2D current = index == origin ? start : point(index, columns, minX, minY, anchor, step);
            if (current.distance(target) <= step * 2 && clear(map, current, target, occupied)) {
                List<Point2D> path = new ArrayList<>();
                path.add(target);
                for (int at = index; at != -1; at = previous[at]) {
                    path.add(at == origin ? start : point(at, columns, minX, minY, anchor, step));
                }
                Collections.reverse(path);
                return simplify(map, path, occupied);
            }
            int column = index % columns;
            int row = index / columns;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int x = column + dx;
                    int y = row + dy;
                    if (x < 0 || y < 0 || x >= columns || y >= rows) continue;
                    int next = y * columns + x;
                    if (closed[next]) continue;
                    double cost = costs[index] + step * Math.hypot(dx, dy);
                    if (cost >= costs[next]) continue;
                    Point candidate = point(next, columns, minX, minY, anchor, step);
                    if (!clear(map, current, candidate, occupied)) continue;
                    costs[next] = cost;
                    previous[next] = index;
                    open.add(new Node(next, cost + candidate.distance(target)));
                }
            }
        }
        return List.of();
    }

    private static Point point(int index, int columns, int minX, int minY, Point start, int step) {
        return new Point(start.x + (index % columns + minX) * step,
                start.y + (index / columns + minY) * step);
    }

    private static Point rounded(Point2D point) {
        return new Point((int) Math.round(point.getX()), (int) Math.round(point.getY()));
    }

    private static boolean clear(MapCollision map, Point2D from, Point2D to, List<? extends Point2D> occupied) {
        Point first = rounded(from);
        Point last = rounded(to);
        if (!map.isSegmentWalkable(first.x, first.y, last.x, last.y)) return false;
        // El redondeo de pasos pequenos puede desviarse una celda de la recta planificada.
        for (int[] offset : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            Point a = new Point(first.x + offset[0], first.y + offset[1]);
            Point b = new Point(last.x + offset[0], last.y + offset[1]);
            if (!map.isWalkable(a.x, a.y)) a = first;
            if (!map.isWalkable(b.x, b.y)) b = last;
            if (!map.isSegmentWalkable(a.x, a.y, b.x, b.y)) return false;
        }
        for (Point2D obstacle : occupied) {
            double distance = from.distance(obstacle);
            double clearance = Math.min(CLEARANCE, distance);
            double length = from.distance(to);
            double outward = length == 0 ? 0
                : ((from.getX() - obstacle.getX()) * (to.getX() - from.getX())
                    + (from.getY() - obstacle.getY()) * (to.getY() - from.getY())) / length;
            if (Line2D.ptSegDist(from.getX(), from.getY(), to.getX(), to.getY(),
                    obstacle.getX(), obstacle.getY()) < clearance - 1e-6
                || (distance < CLEARANCE && outward < 0)) {
                return false;
            }
            if (distance < CLEARANCE && !roundedClear(from, to, obstacle)) return false;
        }
        return true;
    }

    private static boolean roundedClear(Point2D from, Point2D to, Point2D obstacle) {
        double length = from.distance(to);
        if (length == 0) return true;
        double ux = (to.getX() - from.getX()) / length;
        double uy = (to.getY() - from.getY()) / length;
        double limit = Math.min(length, STEP * 2);
        // Revisa cada cambio de celda; un muestreo fijo puede saltarse un bloqueo por redondeo.
        List<Double> crossings = new ArrayList<>();
        crossings.add(0.0);
        crossings.add(limit);
        addCrossings(crossings, from.getX(), ux, limit);
        addCrossings(crossings, from.getY(), uy, limit);
        Collections.sort(crossings);
        Point center = rounded(obstacle);
        double previous = 0;
        for (double crossing : crossings) {
            for (double step : new double[]{crossing, (crossing + previous) / 2}) {
                Point position = rounded(new Point2D.Double(from.getX() + ux * step, from.getY() + uy * step));
                if (position.distance(center) < GameConstants.MIN_ANIMAL_DISTANCE) return false;
            }
            previous = crossing;
        }
        return true;
    }

    private static void addCrossings(List<Double> crossings, double start, double direction, double limit) {
        if (direction == 0) return;
        double end = start + direction * limit;
        int first = (int) Math.floor(Math.min(start, end) - 0.5);
        int last = (int) Math.ceil(Math.max(start, end) - 0.5);
        for (int cell = first; cell <= last; cell++) {
            double distance = (cell + 0.5 - start) / direction;
            if (distance > 0 && distance < limit) crossings.add(distance);
        }
    }

    private static List<int[]> simplify(MapCollision map, List<Point2D> path, List<? extends Point2D> occupied) {
        List<int[]> result = new ArrayList<>();
        int from = 0;
        while (from < path.size() - 1) {
            int to = path.size() - 1;
            while (to > from + 1 && !clear(map, path.get(from), path.get(to), occupied)) to--;
            Point next = rounded(path.get(to));
            result.add(new int[]{next.x, next.y});
            from = to;
        }
        return result;
    }

    private record Node(int index, double priority) {}
}