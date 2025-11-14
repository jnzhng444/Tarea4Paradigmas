package com.dkj.commands;



import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandDispatcher {

    private final String serverId;
    private Map<String, Function<List<String>, String>> table;

    public CommandDispatcher(String serverId) {
        this.serverId = Objects.requireNonNull(serverId);
        initMaps();
    }

    private void initMaps() {
        Map<String, Function<List<String>, String>> t = new HashMap<>();
        // Cliente
        t.put("PING", this::onPing);
        t.put("HELLO", this::onHello);
        t.put("MOVE", this::onMove);
        t.put("BYE", this::onBye);
        // Admin
        t.put("ADMIN", this::onAdmin);
        this.table = Collections.unmodifiableMap(t);
    }

    // --- API pública ---
    public String dispatch(String line) {
        if (line == null || line.isBlank()) return err(400, "Empty");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(0).toUpperCase(Locale.ROOT);

        // Evitamos getOrDefault(...) por la queja de tipos y hacemos null-check
        Function<List<String>, String> fn = table.get(head);
        if (fn == null) return onUnknown(tokens);
        return fn.apply(tokens);
    }

    // ---- Cliente ----
    private String onPing(List<String> tk) {
        if (tk.size() != 1) return err(400, "Usage: PING");
        return "PONG";
    }

    private String onHello(List<String> tk) {
        if (tk.size() < 2) return err(400, "Usage: HELLO PLAYER|SPECTATOR");
        final String role = tk.get(1).toUpperCase(Locale.ROOT);
        if (!role.equals("PLAYER") && !role.equals("SPECTATOR")) {
            return err(422, "Role must be PLAYER or SPECTATOR");
        }
        return "OK " + UUID.randomUUID();
    }

    private String onMove(List<String> tk) {
        if (tk.size() != 2) return err(400, "Usage: MOVE UP|DOWN|LEFT|RIGHT|JUMP");
        final String dir = tk.get(1).toUpperCase(Locale.ROOT);
        if (!Set.of("UP","DOWN","LEFT","RIGHT","JUMP").contains(dir)) {
            return err(422, "Direction must be UP|DOWN|LEFT|RIGHT|JUMP");
        }
        return "ACK MOVE " + dir;
    }

    private String onBye(List<String> tk) {
        if (tk.size() != 1) return err(400, "Usage: BYE");
        return "BYE";
    }

    // ---- Admin ----
    private String onAdmin(List<String> tk) {
        if (tk.size() < 2) return err(400, "Usage: ADMIN <SPAWN|DELETE> ...");
        final String sub = tk.get(1).toUpperCase(Locale.ROOT);
        if (sub.equals("SPAWN"))  return onAdminSpawn(tk);
        if (sub.equals("DELETE")) return onAdminDelete(tk);
        return err(400, "ADMIN subcommand must be SPAWN or DELETE");
    }

    private String onAdminSpawn(List<String> tk) {
        if (tk.size() < 3) return err(400, "Usage: ADMIN SPAWN CROCODILE|FRUIT ...");
        final String kind = tk.get(2).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size() < 4) return err(400, "Usage: ADMIN SPAWN CROCODILE RED|BLUE ...");
            final String color = tk.get(3).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                if (tk.size() != 6) return err(400, "Usage: ADMIN SPAWN CROCODILE RED <LIANA> <ALTURA>");
                final String liana  = tk.get(4);
                final String altura = tk.get(5);
                return "ACK ADMIN SPAWN CROCODILE RED LIANA=" + liana + " ALTURA=" + altura;
            } else if (color.equals("BLUE")) {
                if (tk.size() != 5) return err(400, "Usage: ADMIN SPAWN CROCODILE BLUE <LIANA>");
                final String liana = tk.get(4);
                return "ACK ADMIN SPAWN CROCODILE BLUE LIANA=" + liana;
            } else {
                return err(422, "CROCODILE color must be RED or BLUE");
            }
        } else if (kind.equals("FRUIT")) {
            if (tk.size() != 6) return err(400, "Usage: ADMIN SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>");
            final String liana  = tk.get(3);
            final String altura = tk.get(4);
            final String puntos = tk.get(5);
            return "ACK ADMIN SPAWN FRUIT LIANA=" + liana + " ALTURA=" + altura + " PUNTOS=" + puntos;
        } else {
            return err(422, "SPAWN kind must be CROCODILE or FRUIT");
        }
    }

    private String onAdminDelete(List<String> tk) {
        if (tk.size() < 3) return err(400, "Usage: ADMIN DELETE FRUIT <LIANA> <ALTURA>");
        final String kind = tk.get(2).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(422, "DELETE supports only FRUIT");
        if (tk.size() != 5) return err(400, "Usage: ADMIN DELETE FRUIT <LIANA> <ALTURA>");
        final String liana  = tk.get(3);
        final String altura = tk.get(4);
        return "ACK ADMIN DELETE FRUIT LIANA=" + liana + " ALTURA=" + altura;
    }

    // ---- Desconocidos ----
    private String onUnknown(List<String> tk) {
        return "ERR 400 Unrecognized: " + String.join(" ", tk);
    }

    // ---- Util ----
    private static List<String> tokenize(String line) {
        return Stream.of(line.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private static String err(int code, String text) {
        return "ERR " + code + " " + text;
    }
}
