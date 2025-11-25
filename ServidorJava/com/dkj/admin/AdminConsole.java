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
            System.out.println("[ADMIN] Consola de administracion iniciada.");
            System.out.println("[ADMIN] Escribe 'help' para ver comandos disponibles o 'list' para ver jugadores activos.");
            System.out.println();
            
            while (Boolean.TRUE) {
                System.out.print("admin> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    System.out.println("[INFO] Cerrando consola de administracion...");
                    System.out.println("[INFO] El servidor continuara ejecutandose.");
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

                // Validar comando antes de enviarlo
                String validationError = validateCommand(line);
                if (validationError != null) {
                    System.out.println("[ERROR] " + validationError);
                    System.out.println("[AYUDA] Escribe 'help' para ver la sintaxis correcta.");
                    continue;
                }

                // Contexto de consola: escribe a System.out
                var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), Boolean.TRUE);
                var ctx = new ClientContext(out);

                try {
                    String resp = dispatcher.dispatch(line, ctx);
                    
                    // Formatear respuesta para ser mas amigable
                    if (resp.startsWith("OK") || resp.startsWith("ACK")) {
                        System.out.println("[EXITO] " + resp);
                    } else if (resp.startsWith("ERR")) {
                        System.out.println("[ERROR] " + resp);
                        if (resp.contains("404")) {
                            System.out.println("[AYUDA] El jugador no existe. Usa 'list' para ver jugadores activos.");
                        } else if (resp.contains("400")) {
                            System.out.println("[AYUDA] Sintaxis incorrecta. Usa 'help' para ver ejemplos.");
                        }
                    } else {
                        System.out.println(resp);
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] Error al procesar comando: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error en consola de administracion: " + e.getMessage());
        }
    }

    /**
     * Valida el comando antes de enviarlo al dispatcher.
     * Retorna null si es valido, o un mensaje de error descriptivo.
     */
    private String validateCommand(String line) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length == 0) return null;
        
        String cmd = tokens[0].toUpperCase();
        
        // Validar comando PING
        if (cmd.equals("PING")) {
            if (tokens.length != 1) {
                return "PING no requiere parametros. Uso: PING";
            }
            return null;
        }
        
        // Validar comando ADMIN
        if (cmd.equals("ADMIN")) {
            if (tokens.length < 3) {
                return "Comando ADMIN incompleto. Uso: ADMIN <PLAYER_ID> <SPAWN|DELETE> ...";
            }
            
            String playerId = tokens[1];
            if (playerId.length() < 10) {
                return "PLAYER_ID parece invalido (muy corto). Usa 'list' para ver IDs correctos.";
            }
            
            String subCmd = tokens[2].toUpperCase();
            
            if (subCmd.equals("SPAWN")) {
                return validateSpawnCommand(tokens);
            } else if (subCmd.equals("DELETE")) {
                return validateDeleteCommand(tokens);
            } else {
                return "Subcomando '" + subCmd + "' no reconocido. Usa SPAWN o DELETE.";
            }
        }
        
        // Comando no reconocido
        return "Comando '" + cmd + "' no reconocido. Escribe 'help' para ver comandos disponibles.";
    }
    
    private String validateSpawnCommand(String[] tokens) {
        // ADMIN <PLAYER_ID> SPAWN ...
        if (tokens.length < 4) {
            return "SPAWN incompleto. Usa: SPAWN CROCODILE RED/BLUE o SPAWN FRUIT";
        }
        
        String entityType = tokens[3].toUpperCase();
        
        if (entityType.equals("CROCODILE")) {
            if (tokens.length < 5) {
                return "Falta especificar color del cocodrilo (RED o BLUE).";
            }
            
            String color = tokens[4].toUpperCase();
            
            if (color.equals("RED")) {
                // ADMIN <PID> SPAWN CROCODILE RED <LIANA> <ALTURA>
                if (tokens.length != 7) {
                    return "Sintaxis incorrecta. Uso: ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>";
                }
                
                try {
                    int liana = Integer.parseInt(tokens[5]);
                    if (liana < 1 || liana > 7) {
                        return "LIANA fuera de rango. Debe ser entre 1 y 7 (lianas disponibles).";
                    }
                } catch (NumberFormatException e) {
                    return "LIANA debe ser un numero entero entre 1 y 7.";
                }
                
                try {
                    int altura = Integer.parseInt(tokens[6]);
                    if (altura < 0 || altura > 12) {
                        return "ALTURA fuera de rango. Debe ser entre 0 (suelo) y 12 (tope).";
                    }
                } catch (NumberFormatException e) {
                    return "ALTURA debe ser un numero entero entre 0 y 12.";
                }
                
                return null;
                
            } else if (color.equals("BLUE")) {
                // ADMIN <PID> SPAWN CROCODILE BLUE <LIANA>
                if (tokens.length != 6) {
                    return "Sintaxis incorrecta. Uso: ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>";
                }
                
                try {
                    int liana = Integer.parseInt(tokens[5]);
                    if (liana < 1 || liana > 7) {
                        return "LIANA fuera de rango. Debe ser entre 1 y 7 (lianas disponibles).";
                    }
                } catch (NumberFormatException e) {
                    return "LIANA debe ser un numero entero entre 1 y 7.";
                }
                
                return null;
                
            } else {
                return "Color '" + color + "' no valido. Usa RED o BLUE.";
            }
            
        } else if (entityType.equals("FRUIT")) {
            // ADMIN <PID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
            if (tokens.length != 7) {
                return "Sintaxis incorrecta. Uso: ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>";
            }
            
            try {
                int liana = Integer.parseInt(tokens[4]);
                if (liana < 1 || liana > 7) {
                    return "LIANA fuera de rango. Debe ser entre 1 y 7.";
                }
            } catch (NumberFormatException e) {
                return "LIANA debe ser un numero entero entre 1 y 7.";
            }
            
            try {
                int altura = Integer.parseInt(tokens[5]);
                if (altura < 0 || altura > 12) {
                    return "ALTURA fuera de rango. Debe ser entre 0 (suelo) y 12 (tope).";
                }
            } catch (NumberFormatException e) {
                return "ALTURA debe ser un numero entero entre 0 y 12.";
            }
            
            try {
                int puntos = Integer.parseInt(tokens[6]);
                if (puntos <= 0) {
                    return "PUNTOS debe ser un numero positivo (ej: 100, 200, 300).";
                }
                if (puntos > 10000) {
                    return "PUNTOS demasiado alto. Maximo recomendado: 10000.";
                }
            } catch (NumberFormatException e) {
                return "PUNTOS debe ser un numero entero positivo.";
            }
            
            return null;
            
        } else {
            return "Tipo de entidad '" + entityType + "' no reconocido. Usa CROCODILE o FRUIT.";
        }
    }
    
    private String validateDeleteCommand(String[] tokens) {
        // ADMIN <PID> DELETE FRUIT <LIANA> <ALTURA>
        if (tokens.length < 4) {
            return "DELETE incompleto. Uso: DELETE FRUIT <LIANA> <ALTURA>";
        }
        
        String entityType = tokens[3].toUpperCase();
        
        if (!entityType.equals("FRUIT")) {
            return "Solo se puede eliminar FRUIT. No se pueden eliminar cocodrilos.";
        }
        
        if (tokens.length != 6) {
            return "Sintaxis incorrecta. Uso: ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>";
        }
        
        try {
            int liana = Integer.parseInt(tokens[4]);
            if (liana < 1 || liana > 7) {
                return "LIANA fuera de rango. Debe ser entre 1 y 7.";
            }
        } catch (NumberFormatException e) {
            return "LIANA debe ser un numero entero entre 1 y 7.";
        }
        
        try {
            int altura = Integer.parseInt(tokens[5]);
            if (altura < 0 || altura > 12) {
                return "ALTURA fuera de rango. Debe ser entre 0 (suelo) y 12 (tope).";
            }
        } catch (NumberFormatException e) {
            return "ALTURA debe ser un numero entero entre 0 y 12.";
        }
        
        return null;
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    CONSOLA DE ADMINISTRACION - AYUDA                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("COMANDOS BASICOS:");
        System.out.println("  list              Muestra todos los jugadores activos con sus IDs");
        System.out.println("  ping              Verifica que la consola esta funcionando");
        System.out.println("  help              Muestra este mensaje de ayuda");
        System.out.println("  quit / exit       Cierra la consola (el servidor sigue corriendo)");
        System.out.println();
        System.out.println("COMANDOS DE SPAWNEO:");
        System.out.println("  ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>");
        System.out.println("    └─ Crea un cocodrilo rojo que patrulla una liana");
        System.out.println("    └─ Ejemplo: ADMIN abc123 SPAWN CROCODILE RED 2 6");
        System.out.println();
        System.out.println("  ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>");
        System.out.println("    └─ Crea un cocodrilo azul que desciende por una liana");
        System.out.println("    └─ Ejemplo: ADMIN abc123 SPAWN CROCODILE BLUE 4");
        System.out.println();
        System.out.println("  ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>");
        System.out.println("    └─ Crea una fruta coleccionable");
        System.out.println("    └─ Ejemplo: ADMIN abc123 SPAWN FRUIT 3 8 200");
        System.out.println();
        System.out.println("COMANDOS DE ELIMINACION:");
        System.out.println("  ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>");
        System.out.println("    └─ Elimina una fruta en la posicion especificada");
        System.out.println("    └─ Ejemplo: ADMIN abc123 DELETE FRUIT 3 8");
        System.out.println();
        System.out.println("PARAMETROS VALIDOS:");
        System.out.println("  <PLAYER_ID>       ID del jugador (usar 'list' para verlos)");
        System.out.println("  <LIANA>           Numero de liana: 1 a 7");
        System.out.println("  <ALTURA>          Altura en la liana: 0 (suelo) a 12 (tope)");
        System.out.println("  <PUNTOS>          Puntos de la fruta: 1 a 10000 (recomendado: 100, 200, 300)");
        System.out.println();
        System.out.println("NOTAS IMPORTANTES:");
        System.out.println("  • Los cocodrilos rojos suben y bajan por la liana especificada");
        System.out.println("  • Los cocodrilos azules aparecen en la liana y descienden hasta el suelo");
        System.out.println("  • Solo se pueden eliminar frutas, no cocodrilos");
        System.out.println("  • Todos los comandos son validados antes de ejecutarse");
        System.out.println("  • Los mensajes de error indican exactamente que esta mal");
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void listPlayers() {
        var list = sessions.list();
        if (list.isEmpty()) {
            System.out.println();
            System.out.println("[INFO] No hay jugadores conectados actualmente.");
            System.out.println("[AYUDA] Los jugadores apareceran aqui cuando se conecten al servidor.");
            System.out.println();
            return;
        }
        
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                          JUGADORES ACTIVOS                                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        Integer playerCount = Integer.valueOf(0);
        Integer spectatorCount = Integer.valueOf(0);
        
        System.out.println("JUGADORES:");
        for (ClientContext s : list) {
            if (s.role() == Role.PLAYER && s.playerId() != null) {
                playerCount++;
                System.out.printf("  [%d] %s%n", playerCount, s.playerId().value());
            }
        }
        
        if (playerCount.equals(Integer.valueOf(0))) {
            System.out.println("  (sin jugadores activos)");
        }
        
        System.out.println();
        System.out.println("ESPECTADORES:");
        for (ClientContext s : list) {
            if (s.role() == Role.SPECTATOR) {
                spectatorCount++;
                String id = s.playerId() != null ? s.playerId().value() : "(sin ID)";
                System.out.printf("  [%d] %s%n", spectatorCount, id);
            }
        }
        
        if (spectatorCount.equals(Integer.valueOf(0))) {
            System.out.println("  (sin espectadores activos)");
        }
        
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════════");
        System.out.printf("Total: %d jugador(es), %d espectador(es)%n", playerCount, spectatorCount);
        System.out.println();
        System.out.println("[AYUDA] Usa el ID completo en los comandos ADMIN.");
        System.out.println("        Ejemplo: ADMIN " + (playerCount > 0 ? "<ID_DEL_JUGADOR>" : "abc123...") + " SPAWN CROCODILE RED 2 6");
        System.out.println();
    }
}