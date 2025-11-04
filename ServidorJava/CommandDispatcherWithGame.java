package ServidorJava;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandDispatcherWithGame {

    private final String serverId;
    private final Game game;
    private Map<String, Function<List<String>, String>> table;

    public CommandDispatcherWithGame(String serverId, Game game) {
        this.serverId = Objects.requireNonNull(serverId);
        this.game = Objects.requireNonNull(game);
        initMaps();
    }

    private void initMaps() {
        Map<String, Function<List<String>, String>> t = new HashMap<>();
        t.put("PING", this::onPing);
        t.put("HELLO", this::onHello);
        t.put("MOVE", this::onMove);
        t.put("BYE",  this::onBye);
        t.put("ADMIN", this::onAdmin);
        this.table = Collections.unmodifiableMap(t);
    }

    public String dispatch(String line) {
        if (line == null || line.isBlank()) return err(400, "Empty");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(0).toUpperCase(Locale.ROOT);
        var fn = table.get(head);
        return (fn == null) ? onUnknown(tokens) : fn.apply(tokens);
    }

    // Cliente
    private String onPing(List<String> tk) {
        return (tk.size()==1) ? "PONG" : err(400,"Usage: PING");
    }
    private String onHello(List<String> tk) {
        if (tk.size()<2) return err(400,"Usage: HELLO PLAYER|SPECTATOR");
        String role = tk.get(1).toUpperCase(Locale.ROOT);
        if (!role.equals("PLAYER") && !role.equals("SPECTATOR")) return err(422,"Role must be PLAYER or SPECTATOR");
        return "OK " + UUID.randomUUID();
    }
    private String onMove(List<String> tk) {
        if (tk.size()!=2) return err(400,"Usage: MOVE UP|DOWN|LEFT|RIGHT|JUMP");
        String dir = tk.get(1).toUpperCase(Locale.ROOT);
        if (!Set.of("UP","DOWN","LEFT","RIGHT","JUMP").contains(dir)) return err(422,"Direction must be UP|DOWN|LEFT|RIGHT|JUMP");
        // (Próximo paso) encolar movimiento del jugador
        return "ACK MOVE " + dir;
    }
    private String onBye(List<String> tk) { return (tk.size()==1) ? "BYE" : err(400,"Usage: BYE"); }

    // Admin
    private String onAdmin(List<String> tk) {
        if (tk.size()<2) return err(400,"Usage: ADMIN <SPAWN|DELETE> ...");
        String sub = tk.get(1).toUpperCase(Locale.ROOT);
        return switch (sub) {
            case "SPAWN"  -> onAdminSpawn(tk);
            case "DELETE" -> onAdminDelete(tk);
            default       -> err(400,"ADMIN subcommand must be SPAWN or DELETE");
        };
    }

    private String onAdminSpawn(List<String> tk) {
        if (tk.size()<3) return err(400,"Usage: ADMIN SPAWN CROCODILE|FRUIT ...");
        String kind = tk.get(2).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size()<4) return err(400,"Usage: ADMIN SPAWN CROCODILE RED|BLUE ...");
            String color = tk.get(3).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                if (tk.size()!=6) return err(400,"Usage: ADMIN SPAWN CROCODILE RED <LIANA> <ALTURA>");
                game.spawnCrocodileRed(new LianaId(tk.get(4)), new Height(tk.get(5)));
                return "ACK ADMIN SPAWN CROCODILE RED";
            } else if (color.equals("BLUE")) {
                if (tk.size()!=5) return err(400,"Usage: ADMIN SPAWN CROCODILE BLUE <LIANA>");
                game.spawnCrocodileBlue(new LianaId(tk.get(4)));
                return "ACK ADMIN SPAWN CROCODILE BLUE";
            } else {
                return err(422,"CROCODILE color must be RED or BLUE");
            }
        } else if (kind.equals("FRUIT")) {
            if (tk.size()!=6) return err(400,"Usage: ADMIN SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>");
            game.spawnFruit(new LianaId(tk.get(3)), new Height(tk.get(4)), new Points(tk.get(5)));
            return "ACK ADMIN SPAWN FRUIT";
        } else {
            return err(422,"SPAWN kind must be CROCODILE or FRUIT");
        }
    }

    private String onAdminDelete(List<String> tk) {
        if (tk.size()<3) return err(400,"Usage: ADMIN DELETE FRUIT <LIANA> <ALTURA>");
        String kind = tk.get(2).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(422,"DELETE supports only FRUIT");
        if (tk.size()!=5) return err(400,"Usage: ADMIN DELETE FRUIT <LIANA> <ALTURA>");
        game.deleteFruit(new LianaId(tk.get(3)), new Height(tk.get(4)));
        return "ACK ADMIN DELETE FRUIT";
    }

    private String onUnknown(List<String> tk) { return "ERR 400 Unrecognized: " + String.join(" ", tk); }

    private static List<String> tokenize(String line){
        return Stream.of(line.trim().split("\\s+")).filter(s->!s.isBlank()).collect(Collectors.toList());
    }
    private static String err(int c, String t){ return "ERR " + c + " " + t; }
}
