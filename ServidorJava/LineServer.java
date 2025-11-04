package ServidorJava;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class LineServer {
    private final int port;
    private final String serverId;

    private final CopyOnWriteArrayList<PrintWriter> clients = new CopyOnWriteArrayList<>();

    private final GameEventBus bus = new GameEventBus();
    private final Game game = new Game(bus);
    private final GameLoop loop = new GameLoop(game, "200"); // 200 ms

    public LineServer(int port, String serverId) {
        this.port = port;
        this.serverId = serverId;

        // Suscribir broadcast: cualquier STATE/LEVEL/… se envía a todos los clientes
        bus.subscribe(e -> {
            if (e instanceof StateEvent s) {
                broadcast(s.payload());
            } else if (e instanceof LevelEvent l) {
                broadcast("LEVEL " + l.level() + " SPEED " + l.speed().value());
            } else if (e instanceof ScoreEvent sc) {
                broadcast("SCORE " + sc.player().value() + " " + sc.points().value());
            } else if (e instanceof DeathEvent d) {
                broadcast("DEAD " + d.player().value());
            }
        });
    }

    public void start() {
        loop.start();
        var pool = Executors.newCachedThreadPool();
        try (var server = new ServerSocket(port)) {
            System.out.println("Servidor escuchando en puerto " + port);
            var dispatcher = new CommandDispatcherWithGame(serverId, game); // <- ver B)
            while (true) {
                var client = server.accept();
                pool.execute(() -> handleClient(client, dispatcher));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket client, CommandDispatcherWithGame dispatcher) {
        try (client;
             var in  = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             var out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            clients.add(out);
            String line;
            while ((line = in.readLine()) != null) {
                String response = dispatcher.dispatch(line.trim());
                out.println(response);
                if ("BYE".equals(response)) break;
            }
            clients.remove(out);
        } catch (Exception e) {
            System.err.println("Error con cliente: " + e.getMessage());
        }
    }

    private void broadcast(String line){
        for (var w : clients) {
            try { w.println(line); } catch (Exception ignored) {}
        }
    }
}
