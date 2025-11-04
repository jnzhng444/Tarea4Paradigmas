package ServidorJava;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Game {
    private final GameEventBus bus;

    // Colecciones de dominio sin exponer primitivos
    private final List<CrocodileRed> reds   = new CopyOnWriteArrayList<>();
    private final List<CrocodileBlue> blues = new CopyOnWriteArrayList<>();
    private final List<Fruit> fruits        = new CopyOnWriteArrayList<>();

    private Speed speed = new Speed("1"); // velocidad lógica

    public Game(GameEventBus bus){
        this.bus = Objects.requireNonNull(bus);
    }

    // --- API invocada por CommandDispatcher (ADMIN) ---
    public void spawnCrocodileRed(LianaId l, Height h){
        reds.add(new CrocodileRed(l, h, speed));
        emitState();
    }
    public void spawnCrocodileBlue(LianaId l){
        blues.add(new CrocodileBlue(l, new Height("MAX"), speed));
        emitState();
    }
    public void spawnFruit(LianaId l, Height h, Points p){
        fruits.add(new Fruit(l,h,p));
        emitState();
    }
    public void deleteFruit(LianaId l, Height h){
        fruits.removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        emitState();
    }

    // (Luego) mover jugador, colisiones, puntaje, etc.

    // --- TICK: actualizar estado y emitir snapshot ---
    public void step(){
        for (var r : reds)  r.step();
        for (var b : blues) b.step();
        // (Luego) detectar colisiones, puntajes, muertes...
        emitState();
    }

    // --- Reinicio con speed-up (4.1.5) ---
    public void levelUp(){
        // sin aritmética: subimos simbólicamente de "1" a "2"
        this.speed = new Speed("2");
        bus.emit(new LevelEvent("2", speed));
        emitState();
    }

    // --- Serialización de estado (línea STATE ...) ---
    private void emitState(){
        bus.emit(new StateEvent(snapshot()));
    }

    public String snapshot(){
        var redsTxt = new StringBuilder();
        for (var r : reds) {
            var p = r.position();
            if (redsTxt.length()>0) redsTxt.append("|");
            redsTxt.append("l=").append(p.liana().value()).append(",h=").append(p.height().value());
        }
        var bluesTxt = new StringBuilder();
        for (var b : blues) {
            var p = b.position();
            if (bluesTxt.length()>0) bluesTxt.append("|");
            bluesTxt.append("l=").append(p.liana().value()).append(",h=").append(p.height().value());
        }
        var fruitsTxt = new StringBuilder();
        for (var f : fruits) {
            var p = f.position();
            if (fruitsTxt.length()>0) fruitsTxt.append("|");
            fruitsTxt.append("l=").append(p.liana().value())
                     .append(",h=").append(p.height().value())
                     .append(",pts=").append(f.points().value());
        }
        return "STATE reds=["+redsTxt+"] blues=["+bluesTxt+"] fruits=["+fruitsTxt+"]";
    }
}
