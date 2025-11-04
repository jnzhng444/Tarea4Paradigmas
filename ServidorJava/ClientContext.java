package ServidorJava;

import java.io.PrintWriter;

public final class ClientContext {
    private final PrintWriter out;
    private Role role;           // null hasta HELLO
    private PlayerId playerId;   // solo si es PLAYER

    public ClientContext(PrintWriter out){ this.out = out; }

    public PrintWriter out(){ return out; }
    public Role role(){ return role; }
    public void role(Role r){ this.role = r; }
    public PlayerId playerId(){ return playerId; }
    public void playerId(PlayerId id){ this.playerId = id; }
}
