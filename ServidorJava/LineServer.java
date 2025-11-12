import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

public class LineServer {
    private final Integer port;
    private final String serverId;

    // ★ Fábrica explícita para el dominio
    private final DefaultEntityFactory factory = new DefaultEntityFactory();

    // ★ MatchRegistry ahora recibe la fábrica (para crear Game/entidades)
    private final MatchRegistry matches = new MatchRegistry(factory);
    private final SessionRegistry sessions = new SessionRegistry();

    public LineServer(Integer port, String serverId) {
        this.port = port;
        this.serverId = serverId;
    }

    public void start() {

        var pool = Executors.newCachedThreadPool();
        try (var server = new ServerSocket(port)) {
            System.out.println("Servidor escuchando en puerto " + port);

            // Dispatcher NO cambia: sólo le pasamos el MatchRegistry ya cableado con la fábrica
            var dispatcher = new CommandDispatcherWithGame(serverId, matches, sessions);

            // GUI Admin (ventana)
            SwingUtilities.invokeLater(() -> {
                var win = new AdminWindow(dispatcher, sessions);
                win.showWindow();
            });

            // Consola admin (si la quieres conservar)
            var admin = new Thread(new AdminConsole(dispatcher, sessions), "AdminConsole");
            admin.setDaemon(Boolean.TRUE);
            admin.start();

            while (Boolean.TRUE) {
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
             var out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), Boolean.TRUE)) {

            var ctx = new ClientContext(out);

            String line;
            while ((line = in.readLine()) != null) {
                String response = dispatcher.dispatch(line.trim(), ctx);
                out.println(response);
                if ("BYE".equals(response)) break;
            }

            // limpieza
            matches.removeClient(ctx); // si tu Dispatcher asocia el ctx a una partida, esto lo saca
            sessions.remove(ctx);

        } catch (Exception e) {
            System.err.println("Error con cliente: " + e.getMessage());
        }
    }
}