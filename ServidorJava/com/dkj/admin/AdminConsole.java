package com.dkj.admin;

import com.dkj.commands.CommandDispatcherWithGame;
import com.dkj.model.Role;
import com.dkj.server.ClientContext;
import com.dkj.server.SessionRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class AdminConsole implements Runnable {
    private final CommandDispatcherWithGame dispatcher;
    private final SessionRegistry sessions;

    public AdminConsole(CommandDispatcherWithGame dispatcher, SessionRegistry sessions) {
        this.dispatcher = dispatcher;
        this.sessions = sessions;
    }

    @Override
    public void run() {
        try (var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("[ADMIN] Consola lista. Escribe 'help', 'list' o 'quit'.");
            while (Boolean.TRUE) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    System.out.println("[ADMIN] Saliendo de la consola…");
                    break;
                }

                if (line.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }

                if (line.equalsIgnoreCase("list")) {
                    listPlayers();
                    continue;
                }

                // Contexto de consola: escribe a System.out
                var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), Boolean.TRUE);
                var ctx = new ClientContext(out);

                try {
                    String resp = dispatcher.dispatch(line, ctx);
                    System.out.println(resp);
                } catch (Exception e) {
                    System.out.println("ERR 500 " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[ADMIN] Error consola: " + e.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("""
            Comandos disponibles:
              PING
              ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>
              ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>
              ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
              ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>
              LIST         - muestra los jugadores activos
              HELP         - muestra este mensaje
              QUIT/EXIT    - cierra la consola de admin

            Notas:
              • Usa el <PLAYER_ID> que aparece en el cliente GUI (You: <uuid>).
              • <ALTURA> debe estar en el rango 0-12 (0=abajo, 12=arriba de la liana).
              • <LIANA> debe ser un número entre 1 y 6.
              • Azules desde admin aparecen directo en la liana especificada.
              • 'quit' cierra la consola pero deja el servidor corriendo.
            """);
    }

    private void listPlayers() {
        var list = sessions.list();
        if (list.isEmpty()) {
            System.out.println("No hay jugadores activos.");
            return;
        }
        System.out.println("Jugadores activos:");
        Integer i = Integer.valueOf(1);
        for (ClientContext s : list) {
            if (s.role() == Role.PLAYER && s.playerId() != null) {
                System.out.printf("  %d) %s%n", i++, s.playerId().value());
            }
        }
        if (i.equals(Integer.valueOf(1))) {
            System.out.println("  (sin jugadores PLAYER con id)");
        }
    }
}