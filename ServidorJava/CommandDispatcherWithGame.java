

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
        t.put("BYE",   this::onBye);
        t.put("ADMIN", this::onAdmin);
        this.table = Collections.unmodifiableMap(t);
    }

    public String dispatch(String line, ClientContext ctx) {
        if (line == null || line.isBlank()) return err(400, "Empty");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(0).toUpperCase(Locale.ROOT);
        var fn = table.get(head);
        return (fn == null) ? onUnknown(new Call(tokens, ctx)) : fn.apply(new Call(tokens, ctx));
    }

    // -------------------- Cliente --------------------

    private String onPing(Call c) {
        return (c.tokens.size()==1) ? "PONG" : err(400,"Usage: PING");
    }

    private String onHello(Call c) {
        // Formatos:
        // HELLO PLAYER
        // HELLO SPECTATOR <PLAYER_ID>
        if (c.tokens.size() < 2) return err(400,"Usage: HELLO PLAYER | HELLO SPECTATOR <PLAYER_ID>");
        String role = c.tokens.get(1).toUpperCase(Locale.ROOT);

        if (role.equals("PLAYER")) {
            if (!registry.canAddPlayer()) return err(409,"Players full");
            var pid = new PlayerId();
            c.ctx.playerId(pid);
            c.ctx.role(Role.PLAYER);
            sessions.addPlayer(c.ctx);
            registry.createRoomFor(pid, c.ctx.out());
            return "OK " + pid.value();
        }

        if (role.equals("SPECTATOR")) {
            if (c.tokens.size() != 3) return err(400,"Usage: HELLO SPECTATOR <PLAYER_ID>");
            var target = new PlayerId(c.tokens.get(2));
            boolean ok = registry.attachTo(target, c.ctx.out());
            if (!ok) return err(404, "Player room not found");
            c.ctx.role(Role.SPECTATOR);
            sessions.addSpectator(c.ctx);
            return "OK SPECTATOR " + target.value();
        }

        return err(422,"Role must be PLAYER or SPECTATOR");
    }

    private String onMove(Call c) {
        if (c.tokens.size()!=2) return err(400,"Usage: MOVE UP|DOWN|LEFT|RIGHT|JUMP");
        if (c.ctx.role() != Role.PLAYER) return err(403,"Only PLAYER can MOVE");
        if (c.ctx.playerId() == null)    return err(403,"Not logged in");

        Direction dir = Direction.fromString(c.tokens.get(1));
        if (dir == null) return err(422,"Direction must be UP|DOWN|LEFT|RIGHT|JUMP");

        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(404,"Room not found");

        game.enqueueMove(c.ctx.playerId(), dir);
        return "ACK MOVE " + dir.name();
    }

    private String onBye(Call c) {
        return (c.tokens.size()==1) ? "BYE" : err(400,"Usage: BYE");
    }

    // -------------------- Admin ---------------------
    // Sintaxis obligatoria para escoger partida:
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>
    //   ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
    //   ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>
    private String onAdmin(Call c) {
        var tk = c.tokens;
        if (tk.size() < 3) return err(400,"Usage: ADMIN <PLAYER_ID> <SPAWN|DELETE> ...");

        var target = new PlayerId(tk.get(1));
        var game = registry.gameOf(target);
        if (game == null) return err(404,"Player room not found");

        String sub = tk.get(2).toUpperCase(Locale.ROOT);
        return switch (sub) {
            case "SPAWN"  -> onAdminSpawn(game, tk);
            case "DELETE" -> onAdminDelete(game, tk);
            default       -> err(400,"ADMIN subcommand must be SPAWN or DELETE");
        };
    }

    private String onAdminSpawn(Game game, List<String> tk) {
        if (tk.size()<4) return err(400,"Usage: ADMIN <PID> SPAWN CROCODILE|FRUIT ...");
        String kind = tk.get(3).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size()<5) return err(400,"Usage: ADMIN <PID> SPAWN CROCODILE RED|BLUE ...");
            String color = tk.get(4).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                if (tk.size()!=7) return err(400,"Usage: ADMIN <PID> SPAWN CROCODILE RED <LIANA> <ALTURA>");
                game.spawnCrocodileRed(new LianaId(tk.get(5)), new Height(tk.get(6)));
                return "ACK ADMIN SPAWN CROCODILE RED";
            } else if (color.equals("BLUE")) {
                if (tk.size()!=6) return err(400,"Usage: ADMIN <PID> SPAWN CROCODILE BLUE <LIANA>");
                game.spawnCrocodileBlue(new LianaId(tk.get(5)));
                return "ACK ADMIN SPAWN CROCODILE BLUE";
            } else {
                return err(422,"CROCODILE color must be RED or BLUE");
            }
        } else if (kind.equals("FRUIT")) {
            if (tk.size()!=7) return err(400,"Usage: ADMIN <PID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>");
            game.spawnFruit(new LianaId(tk.get(4)), new Height(tk.get(5)), new Points(tk.get(6)));
            return "ACK ADMIN SPAWN FRUIT";
        } else {
            return err(422,"SPAWN kind must be CROCODILE or FRUIT");
        }
    }

    private String onAdminDelete(Game game, List<String> tk) {
        if (tk.size()<5) return err(400,"Usage: ADMIN <PID> DELETE FRUIT <LIANA> <ALTURA>");
        String kind = tk.get(3).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(422,"DELETE supports only FRUIT");
        if (tk.size()!=6) return err(400,"Usage: ADMIN <PID> DELETE FRUIT <LIANA> <ALTURA>");
        game.deleteFruit(new LianaId(tk.get(4)), new Height(tk.get(5)));
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
    private static String err(int code, String text){ return "ERR " + code + " " + text; }
}
