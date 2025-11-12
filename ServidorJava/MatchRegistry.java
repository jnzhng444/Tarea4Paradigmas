import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MatchRegistry {
    private static final Integer MAX_PLAYERS = Integer.valueOf(2);

    // playerId -> GameRoom
    private final Map<PlayerId, GameRoom> rooms = new LinkedHashMap<>();

    // fábrica explícita para que cada sala cree su Game con Factory Method
    private final DefaultEntityFactory factory;

    public MatchRegistry(DefaultEntityFactory factory) {
        this.factory = factory;
    }

    public Boolean canAddPlayer(){
        return rooms.size() < MAX_PLAYERS;
    }

    /** Crea sala para ese jugador y lo agrega al Game de esa sala */
    public void createRoomFor(PlayerId pid, PrintWriter out){
        GameRoom room = new GameRoom(factory);   // ★ pasa la fábrica a la sala
        room.attach(out);
        room.game().addPlayer(pid);
        rooms.put(pid, room);
    }

    /** Adjunta un writer (cliente) a la sala de ese jugador */
    public Boolean attachTo(PlayerId target, PrintWriter out){
        GameRoom r = rooms.get(target);
        if (r == null) return Boolean.FALSE;
        r.attach(out);
        return Boolean.TRUE;
    }

    /** Obtiene el Game de la sala de un jugador (para MOVE/ADMIN) */
    public Game gameOf(PlayerId pid){
        GameRoom r = rooms.get(pid);
        return (r == null) ? null : r.game();
    }

    /** Si el cliente es PLAYER: remueve la sala completa. Si es SPECTATOR: solo lo detacha. */
    public void removeClient(ClientContext ctx){
        if (ctx == null) return;
        if (ctx.role() == Role.PLAYER && ctx.playerId()!=null){
            GameRoom r = rooms.remove(ctx.playerId());
            if (r != null) {
                r.detach(ctx.out());
                r.stop();
            }
        } else {
            // spectator: podría estar adjunto a cualquier sala; intentamos quitarlo de todas
            for (var r : rooms.values()){
                r.detach(ctx.out());
            }
        }
    }
}