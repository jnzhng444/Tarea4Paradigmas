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
        if (line == null || line.isBlank()) return err(Integer.valueOf(400), "Empty");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(Integer.valueOf(0)).toUpperCase(Locale.ROOT);
        var fn = table.get(head);
        return (fn == null) ? onUnknown(new Call(tokens, ctx)) : fn.apply(new Call(tokens, ctx));
    }

    // -------------------- Cliente --------------------

    private String onPing(Call c) {
        return (c.tokens.size()==Integer.valueOf(1)) ? "PONG" : err(Integer.valueOf(400),"Usage: PING");
    }

    private String onHello(Call c) {
        // Formatos:
        // HELLO PLAYER
        // HELLO SPECTATOR <PLAYER_ID>
        if (c.tokens.size() < Integer.valueOf(2)) return err(Integer.valueOf(400),"Usage: HELLO PLAYER | HELLO SPECTATOR <PLAYER_ID>");
        String role = c.tokens.get(Integer.valueOf(1)).toUpperCase(Locale.ROOT);

        if (role.equals("PLAYER")) {
            if (!registry.canAddPlayer()) return err(Integer.valueOf(409),"Players full");
            var pid = new PlayerId();
            c.ctx.playerId(pid);
            c.ctx.role(Role.PLAYER);
            sessions.addPlayer(c.ctx);
            registry.createRoomFor(pid, c.ctx.out());
            return "OK " + pid.value();
        }

        if (role.equals("SPECTATOR")) {
            if (c.tokens.size() != Integer.valueOf(3)) return err(Integer.valueOf(400),"Usage: HELLO SPECTATOR <PLAYER_INDEX>");
            
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
                        return err(Integer.valueOf(403), "Spectator room is full (max 2)");
                    }
                } else {
                    // El jugador no existe
                    return err(Integer.valueOf(404), "Player " + index + " not found");
                }
            } catch (NumberFormatException e) {
                // Si no es un número, intentar como PlayerId directo
                targetId = new PlayerId(indexOrId);
                ok = registry.attachSpectatorTo(targetId, c.ctx.out());
                if (!ok) return err(Integer.valueOf(409), "Player not found or spectators full");
            }
            
            c.ctx.role(Role.SPECTATOR);
            sessions.addSpectator(c.ctx);
            return "OK SPECTATOR " + (targetId != null ? targetId.value() : indexOrId);
        }

        return err(Integer.valueOf(422),"Role must be PLAYER or SPECTATOR");
    }

    private String onMove(Call c) {
        if (c.tokens.size()!=Integer.valueOf(2)) return err(Integer.valueOf(400),"Usage: MOVE UP|DOWN|LEFT|RIGHT|JUMP");
        if (c.ctx.role() != Role.PLAYER) return err(Integer.valueOf(403),"Only PLAYER can MOVE");
        if (c.ctx.playerId() == null)    return err(Integer.valueOf(403),"Not logged in");

        Direction dir = Direction.fromString(c.tokens.get(Integer.valueOf(1)));
        if (dir == null) return err(Integer.valueOf(422),"Direction must be UP|DOWN|LEFT|RIGHT|JUMP");

        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(Integer.valueOf(404),"Room not found");

        game.enqueueMove(c.ctx.playerId(), dir);
        return "ACK MOVE " + dir.name();
    }

    private String onWin(Call c) {
        if (c.ctx.role() != Role.PLAYER || c.ctx.playerId() == null) {
            return err(Integer.valueOf(403), "Only players can win");
        }
        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(Integer.valueOf(404), "Room not found");
        
        game.handleVictory(c.ctx.playerId());
        return "OK VICTORY";
    }

    private String onBye(Call c) {
        return (c.tokens.size()==Integer.valueOf(1)) ? "BYE" : err(Integer.valueOf(400),"Usage: BYE");
    }

    // -------------------- Admin ---------------------
    // Sintaxis obligatoria para escoger partida:
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>
    //   ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
    //   ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>
    private String onAdmin(Call c) {
        var tk = c.tokens;
        if (tk.size() < Integer.valueOf(3)) return err(Integer.valueOf(400),"Usage: ADMIN <PLAYER_ID> <SPAWN|DELETE> ...");

        var target = new PlayerId(tk.get(Integer.valueOf(1)));
        var game = registry.gameOf(target);
        if (game == null) return err(Integer.valueOf(404),"Player room not found");

        String sub = tk.get(Integer.valueOf(2)).toUpperCase(Locale.ROOT);
        return switch (sub) {
            case "SPAWN"  -> onAdminSpawn(game, tk);
            case "DELETE" -> onAdminDelete(game, tk);
            default       -> err(Integer.valueOf(400),"ADMIN subcommand must be SPAWN or DELETE");
        };
    }

    private String onAdminSpawn(Game game, List<String> tk) {
        if (tk.size()<Integer.valueOf(4)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> SPAWN CROCODILE|FRUIT ...");
        String kind = tk.get(Integer.valueOf(3)).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size()<Integer.valueOf(5)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> SPAWN CROCODILE RED|BLUE ...");
            String color = tk.get(Integer.valueOf(4)).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                if (tk.size()!=Integer.valueOf(7)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> SPAWN CROCODILE RED <LIANA> <ALTURA> (altura debe ser 0-12)");
                String result = game.spawnCrocodileRed(new LianaId(tk.get(Integer.valueOf(5))), new Height(tk.get(Integer.valueOf(6))));
                return result.equals("OK") ? "ACK ADMIN SPAWN CROCODILE RED" : err(Integer.valueOf(422), result);
            } else if (color.equals("BLUE")) {
                // Azul desde admin: necesita LIANA para spawnearlo directo en liana
                if (tk.size()!=Integer.valueOf(6)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> SPAWN CROCODILE BLUE <LIANA>");
                String result = game.spawnCrocodileBlueOnLiana(new LianaId(tk.get(Integer.valueOf(5))));
                return result.equals("OK") ? "ACK ADMIN SPAWN CROCODILE BLUE" : err(Integer.valueOf(422), result);
            } else {
                return err(Integer.valueOf(422),"CROCODILE color must be RED or BLUE");
            }
        } else if (kind.equals("FRUIT")) {
            if (tk.size()!=Integer.valueOf(7)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS> (altura debe ser 0-12)");
            String result = game.spawnFruit(new LianaId(tk.get(Integer.valueOf(4))), new Height(tk.get(Integer.valueOf(5))), new Points(tk.get(Integer.valueOf(6))));
            return result.equals("OK") ? "ACK ADMIN SPAWN FRUIT" : err(Integer.valueOf(422), result);
        } else {
            return err(Integer.valueOf(422),"SPAWN kind must be CROCODILE or FRUIT");
        }
    }

    private String onAdminDelete(Game game, List<String> tk) {
        if (tk.size()<Integer.valueOf(5)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> DELETE FRUIT <LIANA> <ALTURA>");
        String kind = tk.get(Integer.valueOf(3)).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(Integer.valueOf(422),"DELETE supports only FRUIT");
        if (tk.size()!=Integer.valueOf(6)) return err(Integer.valueOf(400),"Usage: ADMIN <PID> DELETE FRUIT <LIANA> <ALTURA>");
        game.deleteFruit(new LianaId(tk.get(Integer.valueOf(4))), new Height(tk.get(Integer.valueOf(5))));
        return "ACK ADMIN DELETE FRUIT";
    }

    private String onUnknown(Call c) {
        return "ERR 400 Unrecognized: " + String.join(" ", c.tokens);
    }

    // -------------------- Util ------------------------------

    private static List<String> tokenize(String line){
        return Stream.of(line.trim().split("\\s+"))
                     .filter(s -> !s.isBlank())
                     .collect(Collectors.toList());
    }
    private static String err(Integer code, String text){ return "ERR " + code + " " + text; }
}