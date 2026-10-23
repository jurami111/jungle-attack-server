package cr.ac.tec.jungleattack.server;

import cr.ac.tec.jungleattack.server.network.Server;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        try (Server server = args.length == 0 ? new Server() : new Server(Integer.parseInt(args[0]))) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
        }
    }
}