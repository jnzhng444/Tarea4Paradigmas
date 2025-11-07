package ServidorJava;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

/** Una sala: (bus + game + loop) + lista de conexiones para broadcast. */
public final class GameRoom {

    private final GameEventBus bus = new GameEventBus();
    private final Game game;
    private final GameLoop loop;

    private final CopyOnWriteArrayList<PrintWriter> outs = new CopyOnWriteArrayList<>();

    public GameRoom(DefaultEntityFactory factory) {
        // ★ el Game se construye con la fábrica explícita (Factory Method visible)
        this.game = new Game(bus, factory);
        this.loop = new GameLoop(game, "16"); // tick textual para respetar “no tipos simples”
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

    public void attach(PrintWriter out){
        if (out != null) outs.add(out);
    }

    public void detach(PrintWriter out){
        if (out != null) outs.remove(out);
    }

    public void stop() {
        try { loop.stop(); } catch (Exception ignored) {}
    }

    private void broadcast(String line){
        for (var w : outs) {
            try { w.println(line); } catch (Exception ignored) {}
        }
    }
}
