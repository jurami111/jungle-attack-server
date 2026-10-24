import cr.ac.tec.jungleattack.server.game.MapFactory;
import cr.ac.tec.jungleattack.server.game.MapGraph;
import cr.ac.tec.jungleattack.server.game.Zone;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

public class MapPreview {
    public static void main(String[] args) throws Exception {
        String name = args.length == 0 ? "docs/mapa-colisiones-actual" : args[0];
        int width = 2500;
        int height = 1900;
        MapGraph map = MapFactory.createCostaRicaMap();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x172125));
        graphics.fillRect(0, 0, width, height);
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 2500 1900\">");
        svg.append("<rect width=\"2500\" height=\"1900\" fill=\"#172125\"/><path fill=\"#64ba86\" d=\"");
        for (int y = 0; y < height; y++) {
            int start = -1;
            for (int x = 0; x <= width; x++) {
                boolean walkable = x < width && map.isWalkable(x, y);
                if (walkable && start < 0) start = x;
                if (!walkable && start >= 0) {
                    graphics.setColor(new Color(0x64ba86));
                    graphics.fillRect(start, y, x - start, 1);
                    svg.append("M").append(start).append(",").append(y).append("h")
                        .append(x - start).append("v1H").append(start).append("z");
                    start = -1;
                }
            }
        }
        svg.append("\"/>");
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        graphics.setColor(new Color(0x839599));
        for (int x = 0; x < width; x += 200) {
            graphics.drawLine(x, 100, x, height - 70);
            graphics.drawString(Integer.toString(x), x + 3, 110);
            svg.append("<path stroke=\"#839599\" opacity=\".35\" d=\"M").append(x)
                .append(",100V1830\"/>");
            text(svg, x + 3, 110, Integer.toString(x), "#a8b6b9", 18);
        }
        for (int y = 200; y < height - 70; y += 200) {
            graphics.drawLine(0, y, width, y);
            graphics.drawString(Integer.toString(y), 5, y - 4);
            svg.append("<path stroke=\"#839599\" opacity=\".35\" d=\"M0,").append(y).append("H2500\"/>");
            text(svg, 5, y - 4, Integer.toString(y), "#a8b6b9", 18);
        }
        graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 8}, 0));
        graphics.setColor(new Color(0xffc65c));
        for (Zone zone : map.getAllZones()) {
            for (Zone neighbor : map.getConnectedZones(zone)) {
                if (zone.getName().compareTo(neighbor.getName()) >= 0) continue;
                List<Point> points = map.getPath(zone, neighbor);
                for (int i = 1; i < points.size(); i++) {
                    Point from = points.get(i - 1);
                    Point to = points.get(i);
                    graphics.drawLine(from.x, from.y, to.x, to.y);
                    svg.append("<path stroke=\"#ffc65c\" stroke-width=\"2\" stroke-dasharray=\"8 8\" d=\"M")
                        .append(from.x).append(",").append(from.y).append("L")
                        .append(to.x).append(",").append(to.y).append("\"/>");
                }
            }
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        for (Zone zone : map.getAllZones()) {
            int x = zone.getCenterX();
            int y = zone.getCenterY();
            String color = map.isWalkable(x, y) ? "#ffffff" : "#ff5959";
            String label = zone.getName() + " (" + x + "," + y + ")";
            graphics.setColor(Color.decode(color));
            graphics.fillOval(x - 7, y - 7, 14, 14);
            graphics.drawString(label, x - 100, y - 20);
            svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"7\" fill=\"")
                .append(color).append("\"/>");
            text(svg, x - 100, y - 20, label, color, 20);
        }
        String title = "Caminabilidad real: MapCollision.isWalkable(x,y)";
        String legend = "VERDE: caminable | OSCURO: bloqueado | AMARILLO: rutas con puntos intermedios | ROJO: centro no caminable";
        graphics.setColor(Color.WHITE);
        graphics.drawString(title, 30, 40);
        graphics.drawString(legend, 30, 75);
        text(svg, 30, 40, title, "#ffffff", 26);
        text(svg, 30, 75, legend, "#ffffff", 20);
        svg.append("</svg>");
        graphics.dispose();
        Path png = Path.of(name + ".png");
        Files.createDirectories(png.toAbsolutePath().getParent());
        ImageIO.write(image, "png", png.toFile());
        Files.writeString(Path.of(name + ".svg"), svg.toString());
        System.out.println("Generated " + name + ".png and .svg");
    }

    private static void text(StringBuilder svg, int x, int y, String value, String fill, int size) {
        svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
            .append("\" fill=\"").append(fill).append("\" font-family=\"sans-serif\" font-size=\"")
            .append(size).append("\">").append(value).append("</text>");
    }
}
