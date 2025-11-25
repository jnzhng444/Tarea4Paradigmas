package com.dkj.commands;

import com.dkj.game.Game;
import com.dkj.model.Direction;
import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.PlayerId;
import com.dkj.model.Points;
import com.dkj.model.Role;
import com.dkj.server.ClientContext;
import com.dkj.server.MatchRegistry;
import com.dkj.server.SessionRegistry;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandDispatcherWithGame {

    private final String serverId;
    private final MatchRegistry registry;
    private final SessionRegistry sessions;
    private Map<String, Function<Call, String>> table;

    /** Encapsula tokens + contexto por conexión */
    private static final class Call {
        final List<String> tokens;
        final ClientContext ctx;
        Call(List<String> t, ClientContext c){ this.tokens = t; this.ctx = c; }
    }

    public CommandDispatcherWithGame(String serverId, MatchRegistry registry, SessionRegistry sessions) {
        this.serverId  = Objects.requireNonNull(serverId);
        this.registry  = Objects.requireNonNull(registry);
        this.sessions  = Objects.requireNonNull(sessions);
        initMaps();
    }

    private void initMaps() {
        Map<String, Function<Call, String>> t = new HashMap<>();
        t.put("PING",  this::onPing);
        t.put("HELLO", this::onHello);
        t.put("MOVE",  this::onMove);
        t.put("WIN",   this::onWin);
        t.put("BYE",   this::onBye);
        t.put("ADMIN", this::onAdmin);
        this.table = Collections.unmodifiableMap(t);
    }

    public String dispatch(String line, ClientContext ctx) {
        if (line == null || line.isBlank()) return err(Integer.valueOf(400), "Comando vacio. Escribe un comando valido (ej: PING, HELLO, MOVE).");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(Integer.valueOf(0)).toUpperCase(Locale.ROOT);
        var fn = table.get(head);
        return (fn == null) ? onUnknown(new Call(tokens, ctx)) : fn.apply(new Call(tokens, ctx));
    }

    // -------------------- Cliente --------------------

    private String onPing(Call c) {
        return (c.tokens.size()==Integer.valueOf(1)) ? "PONG" : err(Integer.valueOf(400),"PING no requiere parametros. Uso correcto: PING");
    }

    private String onHello(Call c) {
        // Formatos:
        // HELLO PLAYER
        // HELLO SPECTATOR <PLAYER_ID>
        if (c.tokens.size() < Integer.valueOf(2)) return err(Integer.valueOf(400),"Comando HELLO incompleto. Uso: HELLO PLAYER o HELLO SPECTATOR <PLAYER_ID>");
        String role = c.tokens.get(Integer.valueOf(1)).toUpperCase(Locale.ROOT);

        if (role.equals("PLAYER")) {
            if (!registry.canAddPlayer()) return err(Integer.valueOf(409),"Servidor lleno. No se pueden agregar mas jugadores (maximo: 2 jugadores activos).");
            var pid = new PlayerId();
            c.ctx.playerId(pid);
            c.ctx.role(Role.PLAYER);
            sessions.addPlayer(c.ctx);
            registry.createRoomFor(pid, c.ctx.out());
            return "OK " + pid.value();
        }

        if (role.equals("SPECTATOR")) {
            if (c.tokens.size() != Integer.valueOf(3)) return err(Integer.valueOf(400),"Falta el ID del jugador. Uso: HELLO SPECTATOR <PLAYER_INDEX>");
            
            String indexOrId = c.tokens.get(Integer.valueOf(2));
            Boolean ok = Boolean.FALSE;
            PlayerId targetId = null;
            
            // Intentar parsear como índice (1, 2, etc)
            try {
                int index = Integer.parseInt(indexOrId);
                targetId = registry.getPlayerIdByIndex(index);
                if (targetId != null) {
                    ok = registry.attachSpectatorByIndex(index, c.ctx.out());
                    if (!ok) {
                        // El jugador existe pero la sala está llena
                        return err(Integer.valueOf(403), "Sala de espectadores llena. El jugador " + index + " ya tiene el maximo de espectadores (maximo: 2).");
                    }
                } else {
                    // El jugador no existe
                    return err(Integer.valueOf(404), "Jugador " + index + " no encontrado. Verifica que el jugador este conectado.");
                }
            } catch (NumberFormatException e) {
                // Si no es un número, intentar como PlayerId directo
                targetId = new PlayerId(indexOrId);
                ok = registry.attachSpectatorTo(targetId, c.ctx.out());
                if (!ok) return err(Integer.valueOf(409), "Jugador no encontrado o sala de espectadores llena (maximo: 2 espectadores por jugador).");
            }
            
            c.ctx.role(Role.SPECTATOR);
            sessions.addSpectator(c.ctx);
            return "OK SPECTATOR " + (targetId != null ? targetId.value() : indexOrId);
        }

        return err(Integer.valueOf(422),"Rol invalido. Debe ser PLAYER o SPECTATOR. Uso: HELLO PLAYER o HELLO SPECTATOR <ID>");
    }

    private String onMove(Call c) {
        if (c.tokens.size()!=Integer.valueOf(2)) return err(Integer.valueOf(400),"Comando MOVE incompleto. Uso: MOVE <direccion> (UP, DOWN, LEFT, RIGHT, JUMP)");
        if (c.ctx.role() != Role.PLAYER) return err(Integer.valueOf(403),"Solo los jugadores pueden moverse. Los espectadores no pueden usar el comando MOVE.");
        if (c.ctx.playerId() == null)    return err(Integer.valueOf(403),"No estas autenticado. Usa HELLO PLAYER primero para conectarte como jugador.");

        Direction dir = Direction.fromString(c.tokens.get(Integer.valueOf(1)));
        if (dir == null) return err(Integer.valueOf(422),"Direccion '" + c.tokens.get(Integer.valueOf(1)) + "' invalida. Usa: UP, DOWN, LEFT, RIGHT o JUMP");

        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(Integer.valueOf(404),"Sala de juego no encontrada. Reconectate con HELLO PLAYER.");

        game.enqueueMove(c.ctx.playerId(), dir);
        return "ACK MOVE " + dir.name();
    }

    private String onWin(Call c) {
        if (c.ctx.role() != Role.PLAYER || c.ctx.playerId() == null) {
            return err(Integer.valueOf(403), "Solo los jugadores pueden ganar. Los espectadores no pueden usar WIN.");
        }
        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(Integer.valueOf(404), "Sala de juego no encontrada. Reconectate con HELLO PLAYER.");
        
        game.handleVictory(c.ctx.playerId());
        return "OK VICTORY";
    }

    private String onBye(Call c) {
        return (c.tokens.size()==Integer.valueOf(1)) ? "BYE" : err(Integer.valueOf(400),"BYE no requiere parametros. Uso correcto: BYE");
    }

    // -------------------- Admin ---------------------
    // Sintaxis obligatoria para escoger partida:
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>
    //   ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
    //   ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>
    private String onAdmin(Call c) {
        var tk = c.tokens;
        if (tk.size() < Integer.valueOf(3)) return err(Integer.valueOf(400),"Comando ADMIN incompleto. Uso: ADMIN <PLAYER_ID> <SPAWN|DELETE> ... (usa 'help' en la consola admin para ver ejemplos)");

        var target = new PlayerId(tk.get(Integer.valueOf(1)));
        var game = registry.gameOf(target);
        if (game == null) return err(Integer.valueOf(404),"Jugador '" + tk.get(Integer.valueOf(1)) + "' no encontrado o no tiene sala activa. Verifica el ID con el comando 'list' en la consola admin.");

        String sub = tk.get(Integer.valueOf(2)).toUpperCase(Locale.ROOT);
        return switch (sub) {
            case "SPAWN"  -> onAdminSpawn(game, tk);
            case "DELETE" -> onAdminDelete(game, tk);
            default       -> err(Integer.valueOf(400),"Subcomando ADMIN '" + sub + "' invalido. Debe ser SPAWN o DELETE.");
        };
    }

    private String onAdminSpawn(Game game, List<String> tk) {
        if (tk.size()<Integer.valueOf(4)) return err(Integer.valueOf(400),"Comando SPAWN incompleto. Especifica el tipo de entidad: CROCODILE o FRUIT");
        String kind = tk.get(Integer.valueOf(3)).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size()<Integer.valueOf(5)) return err(Integer.valueOf(400),"Falta especificar el color del cocodrilo. Uso: SPAWN CROCODILE <RED|BLUE> ...");
            String color = tk.get(Integer.valueOf(4)).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                if (tk.size()!=Integer.valueOf(7)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: SPAWN CROCODILE RED <LIANA> <ALTURA>\nLIANA: 1-7, ALTURA: 0-12");
                String result = game.spawnCrocodileRed(new LianaId(tk.get(Integer.valueOf(5))), new Height(tk.get(Integer.valueOf(6))));
                return result.equals("OK") ? "ACK ADMIN SPAWN CROCODILE RED" : err(Integer.valueOf(422), "Error al crear cocodrilo rojo: " + result);
            } else if (color.equals("BLUE")) {
                // Azul desde admin: necesita LIANA para spawnearlo directo en liana
                if (tk.size()!=Integer.valueOf(6)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: SPAWN CROCODILE BLUE <LIANA>\nLIANA: 1-7");
                String result = game.spawnCrocodileBlueOnLiana(new LianaId(tk.get(Integer.valueOf(5))));
                return result.equals("OK") ? "ACK ADMIN SPAWN CROCODILE BLUE" : err(Integer.valueOf(422), "Error al crear cocodrilo azul: " + result);
            } else {
                return err(Integer.valueOf(422),"Color '" + color + "' invalido para cocodrilo. Usa RED (rojo que patrulla) o BLUE (azul que desciende).");
            }
        } else if (kind.equals("FRUIT")) {
            if (tk.size()!=Integer.valueOf(7)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>\nLIANA: 1-7, ALTURA: 0-12, PUNTOS: valor positivo");
            String result = game.spawnFruit(new LianaId(tk.get(Integer.valueOf(4))), new Height(tk.get(Integer.valueOf(5))), new Points(tk.get(Integer.valueOf(6))));
            return result.equals("OK") ? "ACK ADMIN SPAWN FRUIT" : err(Integer.valueOf(422), "Error al crear fruta: " + result);
        } else {
            return err(Integer.valueOf(422),"Tipo de entidad '" + kind + "' invalido. Usa CROCODILE (cocodrilos) o FRUIT (frutas).");
        }
    }

    private String onAdminDelete(Game game, List<String> tk) {
        if (tk.size()<Integer.valueOf(5)) return err(Integer.valueOf(400),"Comando DELETE incompleto. Uso: DELETE FRUIT <LIANA> <ALTURA>");
        String kind = tk.get(Integer.valueOf(3)).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(Integer.valueOf(422),"Solo se pueden eliminar FRUIT (frutas). Los cocodrilos no se pueden eliminar con DELETE.");
        if (tk.size()!=Integer.valueOf(6)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: DELETE FRUIT <LIANA> <ALTURA>\nLIANA: 1-7, ALTURA: 0-12");
        game.deleteFruit(new LianaId(tk.get(Integer.valueOf(4))), new Height(tk.get(Integer.valueOf(5))));
        return "ACK ADMIN DELETE FRUIT";
    }

    private String onUnknown(Call c) {
        String cmd = c.tokens.isEmpty() ? "(vacio)" : c.tokens.get(Integer.valueOf(0));
        return "ERR 400 Comando '" + cmd + "' no reconocido. Comandos validos: PING, HELLO, MOVE, WIN, BYE, ADMIN";
    }

    // -------------------- Util ------------------------------

    private static List<String> tokenize(String line){
        return Stream.of(line.trim().split("\\s+"))
                     .filter(s -> !s.isBlank())
                     .collect(Collectors.toList());
    }
    private static String err(Integer code, String text){ return "ERR " + code + " " + text; }
}