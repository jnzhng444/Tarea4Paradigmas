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

    private final Map<PlayerId, Player> players = new HashMap<>();

    private Speed speed = new Speed("0.1");

    // ⭐ Spawner de cocodrilos azules
    private Integer ticksSinceLastBlueSpawn = Integer.valueOf(0);
    private static final Integer BLUE_SPAWN_INTERVAL = Integer.valueOf(120); // cada 120 ticks (~6 segundos)

    private final Level level;
    private final PhysicsEngine physics;

    public Game(final GameEventBus bus, final DefaultEntityFactory factory){
        this.bus = Objects.requireNonNull(bus);
        this.factory = Objects.requireNonNull(factory);

        this.level   = new Level();
        this.physics = new PhysicsEngine(level);

        // Spawneo inicial solo de rojos y frutas
        spawnInitialEntities();
        
        emitState();
    }

    private void spawnInitialEntities() {
        // Spawn 1 cocodrilo rojo en liana 3, altura 6
        safeSpawnRed(new LianaId("3"), new Height("6"));
        
        // Spawn 1 fruta en liana 2, altura 8, con 100 puntos
        safeSpawnFruit(new LianaId("2"), new Height("8"), new Points("100"));
    }

    // ===== API ADMIN =====
    public void spawnCrocodileRed(final LianaId l, final Height h){
        safeSpawnRed(l, h);
        emitState();
    }

    public void spawnCrocodileBlue(final Height platformHeight){
        // Crea un azul que empieza caminando en la plataforma
        var blue = factory.newBlue(platformHeight, speed);
        blues.add(blue);
        level.addCrocBlue(blue);
        emitState();
    }

    public void spawnFruit(final LianaId l, final Height h, final Points p){
        safeSpawnFruit(l, h, p);
        emitState();
    }

    public void deleteFruit(final LianaId l, final Height h){
        fruits.removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        level.fruits().removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        emitState();
    }

    // ===== PLAYERS =====
    public void addPlayer(final PlayerId id){
        physics.addPlayer(id);
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }

    public void removePlayer(final PlayerId id){
        physics.removePlayer(id);
        players.remove(id);
        emitState();
    }

    public void enqueueMove(final PlayerId id, final Direction dir){
        physics.enqueueMove(id, dir);
    }

    public void step(){
        Float dt = 0.05f;

        physics.update(dt);

        // Actualizar cocodrilos rojos
        System.out.println("[GAME STEP] Updating " + reds.size() + " red crocodiles");
        for (var r : reds) {
            r.step();
        }

        // Actualizar cocodrilos azules
        System.out.println("[GAME STEP] Updating " + blues.size() + " blue crocodiles");
        List<CrocodileBlue> bluesToRemove = new ArrayList<>();
        for (var b : blues) {
            CrocodileBlue updated = b.step(level);
            if (updated == null) {
                bluesToRemove.add(b);
            }
        }
        blues.removeAll(bluesToRemove);
        level.crocodileBlues().removeAll(bluesToRemove);

        // SPAWNER de azules
        ticksSinceLastBlueSpawn = ticksSinceLastBlueSpawn + 1;
        if (ticksSinceLastBlueSpawn >= BLUE_SPAWN_INTERVAL) {
            System.out.println("[GAME] Spawning new blue crocodile");
            spawnCrocodileBlue(new Height("12"));
            ticksSinceLastBlueSpawn = Integer.valueOf(0);
        }

        // Limpiar frutas
        fruits.removeIf(f -> f.isCollected());
        level.fruits().removeIf(f -> f.isCollected());

        emitState();
    }
    // ===== ADMIN CONSOLE =====
    public String runAdminCommand(String line) {
        try {
            String[] toks = line.trim().split("\\s+");
            if (toks.length < Integer.valueOf(2)) return "ERR sintaxis";

            String cmd = toks[Integer.valueOf(0)].toLowerCase(Locale.ROOT);
            if ("spawn".equals(cmd)) {
                if (toks.length < Integer.valueOf(3)) return "ERR sintaxis";
                String what = toks[Integer.valueOf(1)].toLowerCase(Locale.ROOT);

                Map<String,String> kv = new HashMap<>();
                for (Integer i = Integer.valueOf(2); i < toks.length; i = i + 1) {
                    String[] kvp = toks[i].split("=", Integer.valueOf(2));
                    if (kvp.length == Integer.valueOf(2)) kv.put(kvp[Integer.valueOf(0)].toLowerCase(Locale.ROOT), kvp[Integer.valueOf(1)]);
                }

                switch (what) {
                    case "red" -> {
                        String lStr = kv.get("l");
                        String hStr = kv.getOrDefault("h", "6");
                        if (lStr == null) return "ERR falta l";
                        Boolean ok = safeSpawnRed(new LianaId(lStr), new Height(hStr));
                        emitState();
                        return ok ? "OK red" : "ERR liana";
                    }
                    case "blue" -> {
                        String hStr = kv.getOrDefault("h", "12");
                        spawnCrocodileBlue(new Height(hStr));
                        return "OK blue";
                    }
                    case "fruit" -> {
                        String lStr = kv.get("l");
                        String hStr = kv.getOrDefault("h", "8");
                        String ptsStr = kv.getOrDefault("pts", "100");
                        if (lStr == null) return "ERR falta l";
                        Boolean ok = safeSpawnFruit(new LianaId(lStr), new Height(hStr), new Points(ptsStr));
                        emitState();
                        return ok ? "OK fruit" : "ERR liana";
                    }
                    default -> {
                        return "ERR tipo";
                    }
                }
            } else if ("delete".equals(cmd)) {
                if (toks.length < Integer.valueOf(3)) return "ERR sintaxis";
                String what = toks[Integer.valueOf(1)].toLowerCase(Locale.ROOT);

                Map<String,String> kv = new HashMap<>();
                for (Integer i = Integer.valueOf(2); i < toks.length; i = i + 1) {
                    String[] kvp = toks[i].split("=", Integer.valueOf(2));
                    if (kvp.length == Integer.valueOf(2)) kv.put(kvp[Integer.valueOf(0)].toLowerCase(Locale.ROOT), kvp[Integer.valueOf(1)]);
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

            if (playersTxt.length() > Integer.valueOf(0)) playersTxt.append("|");
            playersTxt.append("id=").append(id.value())
                    .append(",x=").append(String.format("%.1f", phys.x))
                    .append(",y=").append(String.format("%.1f", phys.y))
                    .append(",onLiana=").append(phys.onLiana ? "1" : "0")
                    .append(",score=").append(phys.score);
        }

        var redsTxt = new StringBuilder();
        for (var r : reds) {
            var p = r.position();
            if (redsTxt.length() > Integer.valueOf(0)) redsTxt.append("|");
            redsTxt.append("l=").append(p.liana().value())
                .append(",h=").append(p.height().value());
        }

        var bluesTxt = new StringBuilder();
        for (var b : blues) {
            var p = b.position();
            if (bluesTxt.length() > Integer.valueOf(0)) bluesTxt.append("|");
            
            if (b.getState() == CrocodileBlue.CrocodileBlueState.WALKING_ON_PLATFORM) {
                bluesTxt.append("state=walking")
                    .append(",x=").append(String.format("%.1f", b.getPlatformX()))
                    .append(",h=").append(p.height().value());
                
                // Log para debug
                if (Math.random() < 0.05) {
                    System.out.println("[SNAPSHOT BLUE WALKING] x=" + String.format("%.1f", b.getPlatformX()));
                }
            } else {
                bluesTxt.append("state=descending")
                    .append(",l=").append(p.liana().value())
                    .append(",h=").append(p.height().value());
            }
        }

        var fruitsTxt = new StringBuilder();
        for (var f : fruits) {
            var p = f.position();
            if (fruitsTxt.length() > Integer.valueOf(0)) fruitsTxt.append("|");
            fruitsTxt.append("l=").append(p.liana().value())
                    .append(",h=").append(p.height().value())
                    .append(",pts=").append(f.points().value())
                    .append(",col=").append(f.isCollected() ? "1" : "0");
        }

        return "STATE players=[" + playersTxt + "] reds=[" + redsTxt +
            "] blues=[" + bluesTxt + "] fruits=[" + fruitsTxt + "]";
    }

    // ===== Helpers internos =====
    private Boolean safeSpawnRed(LianaId l, Height h) {
        if (!level.hasLiana(l)) return Boolean.FALSE;
        var red = factory.newRed(l, h, speed);
        reds.add(red);
        level.addCrocRed(red);
        return Boolean.TRUE;
    }

    private Boolean safeSpawnFruit(LianaId l, Height h, Points p) {
        if (!level.hasLiana(l)) return Boolean.FALSE;
        var fruit = factory.newFruit(l, h, p);
        fruits.add(fruit);
        level.addFruit(fruit);
        return Boolean.TRUE;
    }
}