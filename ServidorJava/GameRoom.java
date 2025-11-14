import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

/** Una sala: (bus + game + loop) + lista de conexiones para broadcast. */
public final class GameRoom {

    private static final Integer MAX_SPECTATORS = Integer.valueOf(2);

    private final GameEventBus bus = new GameEventBus();
    private final Game game;
    private final GameLoop loop;

    // Separar player de spectators para controlar el límite
    private PrintWriter playerOut = null;
    private final CopyOnWriteArrayList<PrintWriter> spectatorOuts = new CopyOnWriteArrayList<>();

    public GameRoom(DefaultEntityFactory factory) {
        // ★ el Game se construye con la fábrica explícita (Factory Method visible)
        this.game = new Game(bus, factory);
        this.loop = new GameLoop(game, "16"); 
        this.loop.start();

        // Suscripción a eventos del dominio para difundir a los adjuntos de esta sala
        bus.subscribe(e -> {
            if (e instanceof StateEvent s) {
                broadcast(s.payload());
            } else if (e instanceof LevelEvent l) {
                broadcast("LEVEL " + l.level() + " SPEED " + l.speed().value());
            } else if (e instanceof ScoreEvent sc) {
                broadcast("SCORE " + sc.player().value() + " " + sc.points().value());
            } else if (e instanceof DeathEvent d) {
                broadcast("DEAD " + d.player().value());
            }
        });
    }

    public Game game() { return game; }

    /** Adjunta el PrintWriter del jugador principal */
    public void attach(PrintWriter out){
        if (out != null && playerOut == null) {
            playerOut = out;
        }
    }

    /** Adjunta un espectador. Retorna true si se pudo, false si se alcanzó el límite. */
    public Boolean attachSpectator(PrintWriter out){
        if (out == null) return Boolean.FALSE;
        if (spectatorOuts.size() >= MAX_SPECTATORS) {
            return Boolean.FALSE;
        }
        spectatorOuts.add(out);
        return Boolean.TRUE;
    }

    public void detach(PrintWriter out){
        if (out == null) return;
        if (out == playerOut) {
            playerOut = null;
        } else {
            spectatorOuts.remove(out);
        }
    }

    /** Retorna el número de espectadores conectados */
    public Integer spectatorCount() {
        return spectatorOuts.size();
    }

    public void stop() {
        try { loop.stop(); } catch (Exception ignored) {}
    }

    /** Notifica a los espectadores que el jugador se desconectó */
    public void notifyPlayerDisconnected() {
        for (var w : spectatorOuts) {
            try { 
                w.println("PLAYER_DISCONNECTED"); 
                w.flush();
            } catch (Exception ignored) {}
        }
    }

    private void broadcast(String line){
        // Enviar al jugador
        if (playerOut != null) {
            try { playerOut.println(line); } catch (Exception ignored) {}
        }
        // Enviar a espectadores
        for (var w : spectatorOuts) {
            try { w.println(line); } catch (Exception ignored) {}
        }
    }
}