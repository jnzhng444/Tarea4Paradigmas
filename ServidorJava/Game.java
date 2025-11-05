package ServidorJava;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Núcleo del juego con cola de movimientos.
 * Los jugadores pueden moverse entre lianas y alturas.
 */
public final class Game {

    private final GameEventBus bus;

    private final List<CrocodileRed>  reds   = new CopyOnWriteArrayList<>();
    private final List<CrocodileBlue> blues  = new CopyOnWriteArrayList<>();
    private final List<Fruit>         fruits = new CopyOnWriteArrayList<>();
    private final Map<PlayerId, Player> players = new HashMap<>();

    private final Queue<MoveCommand> moves = new ConcurrentLinkedQueue<>();
    private Speed speed = new Speed("1");

    public Game(GameEventBus bus){
        this.bus = Objects.requireNonNull(bus);
    }

    // --- API ADMIN ---------------------------------------------------------
    public void spawnCrocodileRed(LianaId l, Height h){
        reds.add(new CrocodileRed(l, h, speed));
        emitState();
    }
    public void spawnCrocodileBlue(LianaId l){
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

    // --- PLAYERS -----------------------------------------------------------
    public void addPlayer(PlayerId id){
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }
    public void removePlayer(PlayerId id){
        players.remove(id);
        emitState();
    }

    // --- MOVIMIENTOS -------------------------------------------------------
    public void enqueueMove(PlayerId id, Direction dir){
        if (id == null || dir == null) return;
        moves.offer(new MoveCommand(id, dir));
    }

    // --- LOOP --------------------------------------------------------------
    public void step(){
        processMoves();
        for (var r : reds)  r.step();
        for (var b : blues) b.step();
        emitState();
    }

    private void processMoves(){
        MoveCommand m;
        while ((m = moves.poll()) != null) {
            var p = players.get(m.player());
            if (p == null) continue;
            var pos = p.position();
            int l = Integer.parseInt(pos.liana().value());
            int h = Integer.parseInt(pos.height().value());

            switch (m.dir()) {
                case LEFT  -> l = Math.max(1, l - 1);
                case RIGHT -> l = Math.min(6, l + 1);
                case UP    -> h = Math.min(12, h + 1);
                case DOWN  -> h = Math.max(0, h - 1);
                case JUMP  -> h = Math.min(12, h + 2);
            }

            p.setPosition(new Position(
                new LianaId(Integer.toString(l)),
                new Height(Integer.toString(h))
            ));
        }
    }

    // --- SERIALIZACIÓN -----------------------------------------------------
    private void emitState(){ bus.emit(new StateEvent(snapshot())); }

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

        return "STATE players=[" + playersTxt + "] reds=[" + redsTxt +
               "] blues=[" + bluesTxt + "] fruits=[" + fruitsTxt + "]";
    }
}
