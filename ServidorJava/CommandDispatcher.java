package ServidorJava;

import java.util.UUID;

public class CommandDispatcher {
    private final String serverId;

    public CommandDispatcher(String serverId) {
        this.serverId = serverId;
    }

    public String dispatch(String line) {
        if (line.equalsIgnoreCase("PING")) return "PONG";
        if (line.startsWith("HELLO")) return "OK " + UUID.randomUUID();
        return "ERR 400 Unrecognized: " + line;
    }
}
