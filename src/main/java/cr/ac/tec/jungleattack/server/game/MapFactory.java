package cr.ac.tec.jungleattack.server.game;

import java.awt.Point;
import java.util.List;

public class MapFactory {
    private static final int WALKABLE_AREA_PADDING = 40;

    public static MapGraph createCostaRicaMap() {
        MapGraph graph = new MapGraph();

        // Creaa las 12 Zonas usando las coordenadas EXACTAS de tus puntos de banderas
        Zone conchal = new Zone("Playa Conchal", 331, 327); 
        Zone monteverde = new Zone("Monteverde", 910, 316);  
        Zone zurqui = new Zone("Cerro Zurqui", 1507, 309);  
        Zone braulio = new Zone("Braulio Carrillo", 2105, 305); 
        Zone bahiaBallena = new Zone("Bahia Ballena", 449, 894); 
        Zone manuelAntonio = new Zone("Manuel Antonio", 329, 1459); 
        Zone tortuguero = new Zone("Tortuguero", 924, 1550); 
        Zone chirripo = new Zone("Cerro Chirripo", 916, 1047); 
        Zone tamarindo = new Zone("Tamarindo", 1503, 1499); 
        Zone poas = new Zone("Volcan Poas", 1514, 1042); 
        Zone arenal = new Zone("Volcan Arenal", 2114, 1050); 
        Zone cerroMuerte = new Zone("Cerro de la Muerte", 2110, 1219); 

        // Registramos los nodos
        graph.addZone(conchal);
        graph.addZone(monteverde);
        graph.addZone(zurqui);
        graph.addZone(braulio);
        graph.addZone(bahiaBallena);
        graph.addZone(manuelAntonio);
        graph.addZone(tortuguero);
        graph.addZone(chirripo);
        graph.addZone(tamarindo);
        graph.addZone(poas);
        graph.addZone(arenal);
        graph.addZone(cerroMuerte);

        // Conectar los caminos entre zonas (según las líneas del mapa)
        graph.addPath(conchal, monteverde);
        graph.addPath(monteverde, zurqui);
        graph.addPath(zurqui, braulio);
        graph.addPath(conchal, bahiaBallena, List.of(
            new Point(331, 327), new Point(440, 327), new Point(440, 894), new Point(449, 894)));
        graph.addPath(bahiaBallena, manuelAntonio, List.of(
            new Point(449, 894), new Point(390, 894), new Point(390, 1459), new Point(329, 1459)));
        graph.addPath(manuelAntonio, tortuguero, List.of(
            new Point(329, 1459), new Point(390, 1459), new Point(390, 1228),
            new Point(910, 1228), new Point(910, 1550), new Point(924, 1550)));
        graph.addPath(tortuguero, chirripo, List.of(
            new Point(924, 1550), new Point(910, 1550), new Point(910, 1047), new Point(916, 1047)));
        graph.addPath(chirripo, poas, List.of(
            new Point(916, 1047), new Point(980, 1047), new Point(980, 930), new Point(1180, 930),
            new Point(1180, 895), new Point(1270, 895), new Point(1270, 1042), new Point(1514, 1042)));
        graph.addPath(poas, tamarindo, List.of(
            new Point(1514, 1042), new Point(1510, 1228), new Point(1503, 1499)));
        graph.addPath(tamarindo, cerroMuerte, List.of(
            new Point(1503, 1499), new Point(1503, 1460), new Point(1840, 1460),
            new Point(1840, 1228), new Point(2110, 1228), new Point(2110, 1219)));
        graph.addPath(poas, arenal, List.of(
            new Point(1514, 1042), new Point(1700, 1042), new Point(1700, 895),
            new Point(1930, 895), new Point(1930, 1020), new Point(2114, 1020), new Point(2114, 1050)));
        graph.addPath(arenal, braulio, List.of(
            new Point(2114, 1050), new Point(2114, 1020), new Point(1930, 1020),
            new Point(1930, 690), new Point(2105, 690), new Point(2105, 305)));

        for (Zone from : graph.getAllZones()) {
            for (Zone to : graph.getConnectedZones(from)) {
                List<Point> points = graph.getPath(from, to);
                for (int i = 1; i < points.size(); i++) {
                    Point a = points.get(i - 1);
                    Point b = points.get(i);
                    if (!graph.getCollision().isSegmentWalkable(a.x, a.y, b.x, b.y)) {
                        throw new IllegalStateException("Blocked path: " + from.getName() + " -> " + to.getName());
                    }
                }
            }
        }

        return graph;
    }

    public static MapCollision buildCostaRicaCollisions() {
        MapCollision collision = new MapCollision();

        //Todos los rectángulos caminables
        addExpandedWalkableRect(collision, 360, 1162, 523, 598);
        addExpandedWalkableRect(collision, 360, 1205, 2146, 1251);
        addExpandedWalkableRect(collision, 225, 598, 490, 140);
        addExpandedWalkableRect(collision, 486, 280, 2118, 341);
        addExpandedWalkableRect(collision, 1473, 341, 1554, 717);
        addExpandedWalkableRect(collision, 519, 865, 795, 920);
        addExpandedWalkableRect(collision, 880, 1205, 946, 1012);
        addExpandedWalkableRect(collision, 1092, 874, 1287, 918);
        addExpandedWalkableRect(collision, 245, 1556, 430, 1304);
        addExpandedWalkableRect(collision, 1745, 861, 1906, 923);
        addExpandedWalkableRect(collision, 2082, 282, 2137, 706);
        addExpandedWalkableRect(collision, 2076, 1202, 2153, 1009);
        addExpandedWalkableRect(collision, 1480, 1060, 1539, 1202);
        addExpandedWalkableRect(collision, 1480, 1200, 1539, 1506);
        addExpandedWalkableRect(collision, 888, 1247, 935, 1506);
        addExpandedWalkableRect(collision, 664, 1501, 1688, 1612);
        addExpandedWalkableRect(collision, 1200, 1436, 1859, 1499);
        addExpandedWalkableRect(collision, 1817, 1741, 1864, 1247);
        addExpandedWalkableRect(collision, 1241, 661, 1748, 1132);
        addExpandedWalkableRect(collision, 1858, 667, 2329, 1036);
        addExpandedWalkableRect(collision, 360, 1304, 418, 1162);
        addExpandedWalkableRect(collision, 946, 1171, 1211, 903);

        //Obstáculos circulares / cráteres (3 áreas no caminables)
        collision.addObstacleCircle(1504, 859, 152); // Cráter Poás
        collision.addObstacleCircle(2116, 865, 147); // Volcán Arenal
        collision.addObstacleCircle(1109, 1077, 75);  // Montaña Chirripo

        return collision;
    }

    private static void addExpandedWalkableRect(MapCollision collision, int x1, int y1, int x2, int y2) {
        collision.addWalkableRect(
            Math.min(x1, x2) - WALKABLE_AREA_PADDING,
            Math.min(y1, y2) - WALKABLE_AREA_PADDING,
            Math.max(x1, x2) + WALKABLE_AREA_PADDING,
            Math.max(y1, y2) + WALKABLE_AREA_PADDING);
    }

}