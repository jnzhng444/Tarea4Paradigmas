package ServidorJava;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Núcleo del juego.
 * - Mantiene entidades (cocodrilos rojos/azules, frutas).
 * - Mantiene jugadores (PlayerId -> Player).
 * - Emite STATE/LEVEL/SCORE/DEAD vía GameEventBus (Observer).
 * - step(): avanza el estado y publica snapshot.
 *
 * Nota: el dominio NO expone tipos primitivos; se usan Value Objects.
 * La infraestructura (p.ej., GameLoop) puede parsear números si hace falta.
 */
public final class Game {

    private final GameEventBus bus;

    // Entidades (dominio sin primitivos)
    private final List<CrocodileRed>  reds   = new CopyOnWriteArrayList<>();
    private final List<CrocodileBlue> blues  = new CopyOnWriteArrayList<>();
    private final List<Fruit>         fruits = new CopyOnWriteArrayList<>();

    // Jugadores
    private final Map<PlayerId, Player> players = new HashMap<>();

    // Velocidad lógica (VO)
    private Speed speed = new Speed("1"); // “1” unidades por tick (semántico)

    public Game(GameEventBus bus){
        this.bus = Objects.requireNonNull(bus);
    }

    // ---------------------------
    // API llamada por ADMIN (dispatcher)
    // ---------------------------

    public void spawnCrocodileRed(LianaId l, Height h){
        reds.add(new CrocodileRed(l, h, speed));
        emitState();
    }

    public void spawnCrocodileBlue(LianaId l){
        // Altura inicial simbólica para “caer” (no hacemos aritmética en dominio)
        blues.add(new CrocodileBlue(l, new Height("MAX"), speed));
        emitState();
    }

    public void spawnFruit(LianaId l, Height h, Points p){
        fruits.add(new Fruit(l, h, p));
        emitState();
    }

    public void deleteFruit(LianaId l, Height h){
        fruits.removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        emitState();
    }

    // ---------------------------
    // Gestión de jugadores (HELLO/bye)
    // ---------------------------

    public void addPlayer(PlayerId id){
        // Posición inicial simbólica: liana “1”, altura “0”
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }

    public void removePlayer(PlayerId id){
        players.remove(id);
        emitState();
    }

    // (próximo paso) aplicar movimientos reales por jugador:
    // public void enqueueMove(PlayerId id, Direction dir) { ... }
    // y en step() consumir la cola y actualizar Position.

    // ---------------------------
    // Avance del juego (loop)
    // ---------------------------

    /** Un tick del juego: mueve entidades y emite snapshot. */
    public void step(){
        for (var r : reds)  r.step();   // rojo sube/baja (demostrativo)
        for (var b : blues) b.step();   // azul “cae” (demostrativo)
        // (próximo paso) aplicar movimientos de players, colisiones, puntajes…
        emitState();
    }

    /** Reinicio con aumento de velocidad (cuando Jr salva a DK). */
    public void levelUp(){
        // Sin aritmética en dominio: cambiamos VO de forma simbólica “1” -> “2”
        this.speed = new Speed("2");
        bus.emit(new LevelEvent("2", speed));
        emitState();
    }

    // ---------------------------
    // Serialización de estado
    // ---------------------------

    /** Publica un evento de estado global. */
    private void emitState(){
        bus.emit(new StateEvent(snapshot()));
    }

    /**
     * Devuelve un snapshot legible por el cliente (línea única).
     * Formato: STATE players=[...] reds=[...] blues=[...] fruits=[...]
     */
    public String snapshot(){
        var playersTxt = new StringBuilder();
        for (var p : players.values()){
            var pos = p.position();
            if (playersTxt.length() > 0) playersTxt.append("|");
            playersTxt.append("id=").append(p.id().value())
                      .append(",l=").append(pos.liana().value())
                      .append(",h=").append(pos.height().value());
        }

        var redsTxt = new StringBuilder();
        for (var r : reds) {
            var p = r.position();
            if (redsTxt.length() > 0) redsTxt.append("|");
            redsTxt.append("l=").append(p.liana().value())
                  .append(",h=").append(p.height().value());
        }

        var bluesTxt = new StringBuilder();
        for (var b : blues) {
            var p = b.position();
            if (bluesTxt.length() > 0) bluesTxt.append("|");
            bluesTxt.append("l=").append(p.liana().value())
                   .append(",h=").append(p.height().value());
        }

        var fruitsTxt = new StringBuilder();
        for (var f : fruits) {
            var p = f.position();
            if (fruitsTxt.length() > 0) fruitsTxt.append("|");
            fruitsTxt.append("l=").append(p.liana().value())
                     .append(",h=").append(p.height().value())
                     .append(",pts=").append(f.points().value());
        }

        return "STATE " +
               "players=[" + playersTxt + "] " +
               "reds=["    + redsTxt    + "] " +
               "blues=["   + bluesTxt   + "] " +
               "fruits=["  + fruitsTxt  + "]";
    }
}
