package ServidorJava;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Núcleo del juego con cola de movimientos.
 * Integra DefaultEntityFactory (Factory Method visible) para crear entidades.
 * Evita "magic numbers" con GameRules.
 */
public final class Game {

    // ===== Reglas / límites =====
    public static final class GameRules {
        public static final int MAX_LIANAS = 6;   // ajusta si en tu cliente C usas otro valor
        public static final int HEIGHT_MAX = 12;  // idem
        private GameRules() {}
    }

    // ===== Estado =====
    private final GameEventBus bus;
    private final DefaultEntityFactory factory;   // FÁBRICA 

    private final List<CrocodileRed>  reds   = new CopyOnWriteArrayList<>();
    private final List<CrocodileBlue> blues  = new CopyOnWriteArrayList<>();
    private final List<Fruit>         fruits = new CopyOnWriteArrayList<>();
    private final Map<PlayerId, Player> players = new HashMap<>();

    private final Queue<MoveCommand> moves = new ConcurrentLinkedQueue<>();
    private Speed speed = new Speed("1");        // mantiene tu Speed textual

    public Game(final GameEventBus bus, final DefaultEntityFactory factory){
        this.bus = Objects.requireNonNull(bus);
        this.factory = Objects.requireNonNull(factory);
    }

    // ===== API ADMIN (usa la fábrica) =====
    public void spawnCrocodileRed(final LianaId l, final Height h){
        reds.add(factory.newRed(l, h, speed));
        emitState();
    }

    public void spawnCrocodileBlue(final LianaId l){
        blues.add(factory.newBlue(l, speed));
        emitState();
    }

    public void spawnFruit(final LianaId l, final Height h, final Points p){
        fruits.add(factory.newFruit(l, h, p));
        emitState();
    }

    public void deleteFruit(final LianaId l, final Height h){
        fruits.removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        emitState();
    }

    // ===== PLAYERS =====
    public void addPlayer(final PlayerId id){
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }

    public void removePlayer(final PlayerId id){
        players.remove(id);
        emitState();
    }

    // ===== MOVIMIENTOS =====
    public void enqueueMove(final PlayerId id, final Direction dir){
        if (id == null || dir == null) return;
        moves.offer(new MoveCommand(id, dir));
    }

    // ===== LOOP =====
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
                case RIGHT -> l = Math.min(GameRules.MAX_LIANAS, l + 1);
                case UP    -> h = Math.min(GameRules.HEIGHT_MAX, h + 1);
                case DOWN  -> h = Math.max(0, h - 1);
                case JUMP  -> h = Math.min(GameRules.HEIGHT_MAX, h + 2);
            }

            p.setPosition(new Position(
                new LianaId(Integer.toString(l)),
                new Height(Integer.toString(h))
            ));
        }
    }

    // ===== SERIALIZACIÓN =====
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
