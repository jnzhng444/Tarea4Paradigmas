import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Juego con física pixel-perfect (movimiento libre).
 * El servidor maneja X,Y en píxeles, no coordenadas lógicas.
 */
public final class Game {

    private final GameEventBus bus;
    private final DefaultEntityFactory factory;

    private final List<CrocodileRed>  reds   = new CopyOnWriteArrayList<>();
    private final List<CrocodileBlue> blues  = new CopyOnWriteArrayList<>();
    private final List<Fruit>         fruits = new CopyOnWriteArrayList<>();

    // (legacy lógico, se mantiene si en otra parte lo usan)
    private final Map<PlayerId, Player> players = new HashMap<>();

    private Speed speed = new Speed("1");

    // ===== Mundo y física separados =====
    private final Level level;
    private final PhysicsEngine physics;

    public Game(final GameEventBus bus, final DefaultEntityFactory factory){
        this.bus = Objects.requireNonNull(bus);
        this.factory = Objects.requireNonNull(factory);

        this.level   = new Level();
        this.physics = new PhysicsEngine(level);

        emitState();
    }

    // ===== API ADMIN (pública, compatible con código previo) =====
    public void spawnCrocodileRed(final LianaId l, final Height h){
        safeSpawnRed(l, h);
        emitState();
    }

    public void spawnCrocodileBlue(final LianaId l){
        safeSpawnBlue(l);
        emitState();
    }

    public void spawnFruit(final LianaId l, final Height h, final Points p){
        safeSpawnFruit(l, h, p);
        emitState();
    }

    public void deleteFruit(final LianaId l, final Height h){
        fruits.removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        level.fruits().removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));  // ⭐ AGREGAR
        emitState();
    }

    // ===== PLAYERS =====
    public void addPlayer(final PlayerId id){
        physics.addPlayer(id);

        // Player con posición lógica (legacy)
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }

    public void removePlayer(final PlayerId id){
        physics.removePlayer(id);
        players.remove(id);
        emitState();
    }

    // ===== MOVIMIENTOS =====
    public void enqueueMove(final PlayerId id, final Direction dir){
        physics.enqueueMove(id, dir);
    }

    // ===== LOOP (cada ~50ms para 60 FPS simulado) =====
    public void step(){
        float dt = 0.05f; // 50ms

        physics.update(dt);

        // Mover entidades
        for (var r : reds)  r.step();
        for (var b : blues) b.step();

        // ⭐ LIMPIAR frutas colectadas (borrar completamente)
        fruits.removeIf(f -> f.isCollected());
        level.fruits().removeIf(f -> f.isCollected());

        emitState();
    }

    // ===== ADMIN CONSOLE (opcional) =====
    /**
     * Comandos soportados:
     *   spawn red l=3 h=5
     *   spawn blue l=2
     *   spawn fruit l=4 h=6 pts=200
     *   delete fruit l=4 h=6
     */
    public String runAdminCommand(String line) {
        try {
            String[] toks = line.trim().split("\\s+");
            if (toks.length < 2) return "ERR sintaxis";

            String cmd = toks[0].toLowerCase(Locale.ROOT);
            if ("spawn".equals(cmd)) {
                if (toks.length < 3) return "ERR sintaxis";
                String what = toks[1].toLowerCase(Locale.ROOT);

                Map<String,String> kv = new HashMap<>();
                for (int i = 2; i < toks.length; i++) {
                    String[] kvp = toks[i].split("=", 2);
                    if (kvp.length == 2) kv.put(kvp[0].toLowerCase(Locale.ROOT), kvp[1]);
                }

                String lStr = kv.get("l");
                if (lStr == null) return "ERR falta l";
                LianaId lianaId = new LianaId(lStr);

                switch (what) {
                    case "red" -> {
                        String hStr = kv.getOrDefault("h", "0");
                        boolean ok = safeSpawnRed(lianaId, new Height(hStr));
                        emitState();
                        return ok ? "OK red" : "ERR liana";
                    }
                    case "blue" -> {
                        boolean ok = safeSpawnBlue(lianaId);
                        emitState();
                        return ok ? "OK blue" : "ERR liana";
                    }
                    case "fruit" -> {
                        String hStr   = kv.getOrDefault("h", "0");
                        String ptsStr = kv.getOrDefault("pts", "100");
                        boolean ok = safeSpawnFruit(lianaId, new Height(hStr), new Points(ptsStr));
                        emitState();
                        return ok ? "OK fruit" : "ERR liana";
                    }
                    default -> {
                        return "ERR tipo";
                    }
                }
            } else if ("delete".equals(cmd)) {
                if (toks.length < 3) return "ERR sintaxis";
                String what = toks[1].toLowerCase(Locale.ROOT);

                Map<String,String> kv = new HashMap<>();
                for (int i = 2; i < toks.length; i++) {
                    String[] kvp = toks[i].split("=", 2);
                    if (kvp.length == 2) kv.put(kvp[0].toLowerCase(Locale.ROOT), kvp[1]);
                }

                if ("fruit".equals(what)) {
                    String lStr = kv.get("l");
                    String hStr = kv.get("h");
                    if (lStr == null || hStr == null) return "ERR falta l/h";
                    deleteFruit(new LianaId(lStr), new Height(hStr));
                    return "OK delete fruit";
                }
                return "ERR tipo";
            }

            return "ERR comando";
        } catch (Exception ex) {
            return "ERR " + ex.getMessage();
        }
    }

    // ===== SERIALIZACIÓN =====
    private void emitState(){
        bus.emit(new StateEvent(snapshot()));
    }

    public String snapshot(){
        var playersTxt = new StringBuilder();
        for (var entry : physics.getPlayerPhysics().entrySet()) {
            var id = entry.getKey();
            var phys = entry.getValue();

            if (playersTxt.length() > 0) playersTxt.append("|");
            playersTxt.append("id=").append(id.value())
                      .append(",x=").append(String.format("%.1f", phys.x))
                      .append(",y=").append(String.format("%.1f", phys.y))
                      .append(",onLiana=").append(phys.onLiana ? "1" : "0")
                      .append(",score=").append(phys.score);  // ⭐ AGREGAR SCORE
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
            // Ya no necesitamos verificar isCollected() porque se borran en step()
            var p = f.position();
            if (fruitsTxt.length() > 0) fruitsTxt.append("|");
            fruitsTxt.append("l=").append(p.liana().value())
                     .append(",h=").append(p.height().value())
                     .append(",pts=").append(f.points().value())
                     .append(",col=0");  // Siempre 0 porque las collected ya fueron borradas
        }

        return "STATE players=[" + playersTxt + "] reds=[" + redsTxt +
               "] blues=[" + bluesTxt + "] fruits=[" + fruitsTxt + "]";
    }

    // ===== Helpers internos (validan liana antes de spawnear) =====
    private boolean safeSpawnRed(LianaId l, Height h) {
        if (!level.hasLiana(l)) return false;
        var red = factory.newRed(l, h, speed);
        reds.add(red);
        level.addCrocRed(red);  // ⭐ AGREGAR AL LEVEL
        return true;
    }

    private boolean safeSpawnBlue(LianaId l) {
        if (!level.hasLiana(l)) return false;
        var blue = factory.newBlue(l, speed);
        blues.add(blue);
        level.addCrocBlue(blue);  // ⭐ AGREGAR AL LEVEL
        return true;
    }

    private boolean safeSpawnFruit(LianaId l, Height h, Points p) {
        if (!level.hasLiana(l)) return false;
        var fruit = factory.newFruit(l, h, p);
        fruits.add(fruit);
        level.addFruit(fruit);  // ⭐ AGREGAR AL LEVEL
        return true;
    }
}