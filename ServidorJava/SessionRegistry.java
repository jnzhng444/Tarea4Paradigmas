package ServidorJava;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SessionRegistry {
    private final List<ClientContext> players = new CopyOnWriteArrayList<>();
    private final List<ClientContext> spectators = new CopyOnWriteArrayList<>();
    private final int maxPlayers = 2;   // regla de negocio pedida (2 jugadores)
    // Podés más adelante agregar 2 espectadores por jugador si tu PDF lo exige literalmente.

    public synchronized boolean canAddPlayer(){ return players.size() < maxPlayers; }
    public synchronized void addPlayer(ClientContext ctx){ players.add(ctx); }
    public synchronized void remove(ClientContext ctx){
        players.remove(ctx); spectators.remove(ctx);
    }
    public synchronized void addSpectator(ClientContext ctx){ spectators.add(ctx); }

    public List<ClientContext> allConnections(){
        var all = new ArrayList<ClientContext>(players);
        all.addAll(spectators);
        return all;
    }
}
