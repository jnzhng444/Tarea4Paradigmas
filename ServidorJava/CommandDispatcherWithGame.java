package ServidorJava;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandDispatcherWithGame {

    private final String serverId;
    private final Game game;
    private final SessionRegistry sessions;
    private Map<String, Function<Call, String>> table;

    /** Encapsula tokens + contexto por conexión */
    private static final class Call {
        final List<String> tokens;
        final ClientContext ctx;
        Call(List<String> t, ClientContext c){ this.tokens = t; this.ctx = c; }
    }

    /** Constructor (incluye SessionRegistry) */
    public CommandDispatcherWithGame(String serverId, Game game, SessionRegistry sessions) {
        this.serverId  = Objects.requireNonNull(serverId);
        this.game      = Objects.requireNonNull(game);
        this.sessions  = Objects.requireNonNull(sessions);
        initMaps();
    }

    private void initMaps() {
        Map<String, Function<Call, String>> t = new HashMap<>();
        t.put("PING",  this::onPing);
        t.put("HELLO", this::onHello);
        t.put("MOVE",  this::onMove);
        t.put("BYE",   this::onBye);
        t.put("ADMIN", this::onAdmin);
        this.table = Collections.unmodifiableMap(t);
    }

    /** Entrada principal: ahora recibe (línea + contexto) */
    public String dispatch(String line, ClientContext ctx) {
        if (line == null || line.isBlank()) return err(400, "Empty");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(0).toUpperCase(Locale.ROOT);
        var fn = table.get(head);
        return (fn == null) ? onUnknown(new Call(tokens, ctx)) : fn.apply(new Call(tokens, ctx));
    }

    // -------------------- Handlers Cliente --------------------

    private String onPing(Call c) {
        return (c.tokens.size()==1) ? "PONG" : err(400,"Usage: PING");
    }

    private String onHello(Call c) {
        if (c.tokens.size()<2) return err(400,"Usage: HELLO PLAYER|SPECTATOR");
        String role = c.tokens.get(1).toUpperCase(Locale.ROOT);

        if (role.equals("PLAYER")) {
            if (!sessions.canAddPlayer()) return err(409,"Players full");
            var pid = new PlayerId();
            c.ctx.playerId(pid);
            c.ctx.role(Role.PLAYER);
            sessions.addPlayer(c.ctx);
            game.addPlayer(pid);
            return "OK " + pid.value();
        } else if (role.equals("SPECTATOR")) {
            c.ctx.role(Role.SPECTATOR);
            sessions.addSpectator(c.ctx);
            return "OK SPECTATOR";
        }
        return err(422,"Role must be PLAYER or SPECTATOR");
    }

    /** MOVE que encola al Game; sólo para PLAYER */
    private String onMove(Call c) {
        if (c.tokens.size()!=2) return err(400,"Usage: MOVE UP|DOWN|LEFT|RIGHT|JUMP");
        if (c.ctx.role() != Role.PLAYER) return err(403,"Only PLAYER can MOVE");

        Direction dir = Direction.fromString(c.tokens.get(1));
        if (dir == null) return err(422,"Direction must be UP|DOWN|LEFT|RIGHT|JUMP");

        game.enqueueMove(c.ctx.playerId(), dir);
        return "ACK MOVE " + dir.name();
    }

    private String onBye(Call c) {
        return (c.tokens.size()==1) ? "BYE" : err(400,"Usage: BYE");
    }

    // -------------------- Handlers Admin ---------------------

    private String onAdmin(Call c) {
        var tk = c.tokens;
        if (tk.size()<2) return err(400,"Usage: ADMIN <SPAWN|DELETE> ...");
        String sub = tk.get(1).toUpperCase(Locale.ROOT);
        return switch (sub) {
            case "SPAWN"  -> onAdminSpawn(c);
            case "DELETE" -> onAdminDelete(c);
            default       -> err(400,"ADMIN subcommand must be SPAWN or DELETE");
        };
    }

    private String onAdminSpawn(Call c) {
        var tk = c.tokens;
        if (tk.size()<3) return err(400,"Usage: ADMIN SPAWN CROCODILE|FRUIT ...");
        String kind = tk.get(2).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size()<4) return err(400,"Usage: ADMIN SPAWN CROCODILE RED|BLUE ...");
            String color = tk.get(3).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                // ADMIN SPAWN CROCODILE RED <LIANA> <ALTURA>
                if (tk.size()!=6) return err(400,"Usage: ADMIN SPAWN CROCODILE RED <LIANA> <ALTURA>");
                game.spawnCrocodileRed(new LianaId(tk.get(4)), new Height(tk.get(5)));
                return "ACK ADMIN SPAWN CROCODILE RED";
            } else if (color.equals("BLUE")) {
                // ADMIN SPAWN CROCODILE BLUE <LIANA>
                if (tk.size()!=5) return err(400,"Usage: ADMIN SPAWN CROCODILE BLUE <LIANA>");
                game.spawnCrocodileBlue(new LianaId(tk.get(4)));
                return "ACK ADMIN SPAWN CROCODILE BLUE";
            } else {
                return err(422,"CROCODILE color must be RED or BLUE");
            }
        } else if (kind.equals("FRUIT")) {
            // ADMIN SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
            if (tk.size()!=6) return err(400,"Usage: ADMIN SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>");
            game.spawnFruit(new LianaId(tk.get(3)), new Height(tk.get(4)), new Points(tk.get(5)));
            return "ACK ADMIN SPAWN FRUIT";
        } else {
            return err(422,"SPAWN kind must be CROCODILE or FRUIT");
        }
    }

    private String onAdminDelete(Call c) {
        var tk = c.tokens;
        if (tk.size()<3) return err(400,"Usage: ADMIN DELETE FRUIT <LIANA> <ALTURA>");
        String kind = tk.get(2).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(422,"DELETE supports only FRUIT");
        if (tk.size()!=5) return err(400,"Usage: ADMIN DELETE FRUIT <LIANA> <ALTURA>");
        game.deleteFruit(new LianaId(tk.get(3)), new Height(tk.get(4)));
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

    private static String err(int code, String text){
        return "ERR " + code + " " + text;
    }
}
