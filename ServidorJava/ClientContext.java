package ServidorJava;

import java.io.PrintWriter;

public final class ClientContext {
    private final PrintWriter out;
    private PlayerId playerId;
    private Role role;

    public ClientContext(PrintWriter out){
        this.out = out;
    }

    public PrintWriter out(){ return out; }

    public PlayerId playerId(){ return playerId; }
    public void playerId(PlayerId id){ this.playerId = id; }

    public Role role(){ return role; }
    public void role(Role r){ this.role = r; }
}
