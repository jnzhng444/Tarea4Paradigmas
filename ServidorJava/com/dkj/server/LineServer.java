package com.dkj.server;

import com.dkj.admin.AdminConsole;
import com.dkj.admin.AdminWindow;
import com.dkj.commands.CommandDispatcherWithGame;
import com.dkj.entities.DefaultEntityFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

/**
 * Servidor TCP principal del juego Donkey Kong Jr multijugador.
 * 
 * Implementa un servidor multi-threaded que acepta conexiones de clientes
 * y procesa comandos de red. Cada cliente se maneja en su propio hilo mediante
 * un pool de threads. El servidor coordina partidas, sesiones y administracion.
 * 
 * Arquitectura:
 * - Thread principal: Acepta conexiones en loop infinito
 * - Pool de threads: Maneja cada cliente en hilo separado
 * - Factory: Crea entidades del juego (cocodrilos, frutas)
 * - MatchRegistry: Gestiona salas de juego activas
 * - SessionRegistry: Rastrea todas las sesiones conectadas
 * - Dispatcher: Procesa comandos de protocolo de red
 * - Consolas admin: GUI y terminal para administracion
 * 
 * Protocolo:
 * - Comunicacion basada en lineas de texto (UTF-8)
 * - Comandos: HELLO, PING, MOVE, WIN, BYE, ADMIN
 * - Respuestas: OK, ACK, ERR, PONG, STATE, etc.
 * 
 * Ciclo de vida del cliente:
 * 1. Cliente conecta via TCP
 * 2. Envia HELLO PLAYER o HELLO SPECTATOR
 * 3. Intercambia comandos con servidor
 * 4. Envia BYE o se desconecta
 * 5. Servidor limpia recursos del cliente
 */
public class LineServer {
    private final Integer port;
    private final String serverId;

    // ★ Fábrica explícita para el dominio
    private final DefaultEntityFactory factory = new DefaultEntityFactory();

    // ★ MatchRegistry ahora recibe la fábrica (para crear Game/entidades)
    private final MatchRegistry matches = new MatchRegistry(factory);
    private final SessionRegistry sessions = new SessionRegistry();

    /**
     * Crea un nuevo servidor en el puerto especificado.
     * 
     * @param port Puerto TCP donde escuchara el servidor
     * @param serverId Identificador unico de esta instancia del servidor
     */
    public LineServer(Integer port, String serverId) {
        this.port = port;
        this.serverId = serverId;
    }

    /**
     * Inicia el servidor y comienza a aceptar conexiones.
     * 
     * Proceso de inicio:
     * 1. Crea pool de threads para clientes
     * 2. Abre socket TCP en el puerto configurado
     * 3. Inicializa dispatcher de comandos
     * 4. Lanza consolas de administracion (GUI y terminal)
     * 5. Entra en loop infinito aceptando conexiones
     * 
     * Cada conexion aceptada se delega a handleClient() en un hilo separado.
     * Este metodo bloquea indefinidamente hasta que ocurre una excepcion.
     */
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

    /**
     * Maneja la comunicacion con un cliente individual.
     * 
     * Proceso por cliente:
     * 1. Crea streams de entrada/salida (UTF-8, auto-flush)
     * 2. Crea contexto de cliente (ClientContext)
     * 3. Lee lineas del cliente en loop
     * 4. Despacha cada comando via dispatcher
     * 5. Envia respuesta al cliente
     * 6. Sale del loop si recibe/envia "BYE"
     * 7. Limpia recursos (cierra streams, remueve de registros)
     * 
     * Este metodo se ejecuta en su propio hilo del pool y bloquea
     * hasta que el cliente se desconecta o ocurre un error.
     * 
     * @param client Socket del cliente conectado
     * @param dispatcher Dispatcher de comandos para procesar requests
     */
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