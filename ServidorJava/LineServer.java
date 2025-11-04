package ServidorJava;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class LineServer {
    private final int port;
    private final String serverId;

    public LineServer(int port, String serverId) {
        this.port = port;
        this.serverId = serverId;
    }

    public void start() {
        var pool = Executors.newCachedThreadPool();
        try (var server = new ServerSocket(port)) {
            System.out.println("Servidor escuchando en puerto " + port);

            var dispatcher = new CommandDispatcher(serverId);

            while (true) {
                var client = server.accept();
                pool.execute(() -> handleClient(client, dispatcher));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket client, CommandDispatcher dispatcher) {
        try (client;
             var in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             var out = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8)) {

            String line;
            while ((line = in.readLine()) != null) {
                String response = dispatcher.dispatch(line.trim());
                out.println(response);
                if (response.equals("BYE")) break;
            }
        } catch (Exception e) {
            System.err.println("Error con cliente: " + e.getMessage());
        }
    }
}
