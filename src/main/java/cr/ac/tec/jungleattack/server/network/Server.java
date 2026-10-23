package cr.ac.tec.jungleattack.server.network;

import cr.ac.tec.jungleattack.server.config.GameConstants;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Server implements AutoCloseable {
    private final ServerSocket listener;
    private final MatchManager matchManager = new MatchManager();
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public Server() throws IOException {
        this(GameConstants.PORT);
    }

    public Server(int port) throws IOException {
        listener = new ServerSocket(port);
    }

    public void start() throws IOException {
        System.out.println("Jungle Attack server listening on port " + getPort());
        try {
            while (!listener.isClosed()) {
                Socket socket = listener.accept();
                socket.setTcpNoDelay(true);
                ClientHandler client;
                try {
                    client = new ClientHandler(socket, matchManager);
                } catch (IOException exception) {
                    socket.close();
                    throw exception;
                }
                clients.add(client);
                if (listener.isClosed()) client.close();
                new Thread(() -> {
                    try {
                        client.run();
                    } finally {
                        clients.remove(client);
                    }
                }, "jungle-attack-client").start();
            }
        } catch (SocketException exception) {
            if (!listener.isClosed()) throw exception;
        } finally {
            close();
        }
    }

    public int getPort() { return listener.getLocalPort(); }

    @Override
    public void close() {
        try {
            listener.close();
        } catch (IOException exception) {
            System.err.println("Could not close server socket: " + exception.getMessage());
        }
        for (ClientHandler client : clients) client.close();
        matchManager.close();
    }
}
