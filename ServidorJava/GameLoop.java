package ServidorJava;

import java.util.Objects;
import java.util.concurrent.*;

public final class GameLoop {
    private final Game game;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final String tickMillis; // VO evitar primitivo visible (p.ej. "200")

    public GameLoop(Game game, String tickMillis){
        this.game = Objects.requireNonNull(game);
        this.tickMillis = Objects.requireNonNull(tickMillis);
    }

    public void start(){
        long period = Long.parseLong(tickMillis); // infraestructura, no dominio
        exec.scheduleAtFixedRate(game::step, period, period, TimeUnit.MILLISECONDS);
    }

    public void stop(){ exec.shutdownNow(); }
}
