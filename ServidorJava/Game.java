package ServidorJava;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Juego con física pixel-perfect (movimiento libre).
 * El servidor maneja X,Y en píxeles, no coordenadas lógicas.
 */
public final class Game {

    public static final class GameRules {
        public static final int MAX_LIANAS = 6;
        public static final int HEIGHT_MAX = 12;
        
        // Límites del mundo en píxeles
        public static final float MIN_X = 20.0f;
        public static final float MAX_X = 780.0f;
        public static final float MIN_Y = 0.0f;
        public static final float MAX_Y = 540.0f;
        
        // Física
        public static final float GRAVITY = 800.0f;
        public static final float MOVE_SPEED = 200.0f;
        public static final float JUMP_FORCE = 350.0f;
        public static final float CLIMB_SPEED = 150.0f;
        
        private GameRules() {}
    }
    
    // Plataforma simple
    private static class Platform {
        float x, y, w, h;
        Platform(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    
    // Liana para trepar
    private static class Liana {
        float x, topY, bottomY;
        Liana(float x, float topY, float bottomY) {
            this.x = x; this.topY = topY; this.bottomY = bottomY;
        }
        boolean canGrab(float px, float py, float range) {
            return Math.abs(px - x) < range && py > topY && py < bottomY;
        }
    }
    
    // Estado del jugador (server-side)
    private static class PlayerPhysics {
        float x, y;           // posición en píxeles (x,y es el CENTRO del jugador)
        float vx, vy;         // velocidad
        boolean onGround;
        boolean onLiana;
        int lianaIndex;
        
        PlayerPhysics() {
            x = 150.0f;       // centro horizontal
            y = 490.0f;       // arriba del piso (520 - 30 altura)
            vx = 0;
            vy = 0;
            onGround = true;
            onLiana = false;
            lianaIndex = -1;
        }
    }

    private final GameEventBus bus;
    private final DefaultEntityFactory factory;

    private final List<CrocodileRed>  reds   = new CopyOnWriteArrayList<>();
    private final List<CrocodileBlue> blues  = new CopyOnWriteArrayList<>();
    private final List<Fruit>         fruits = new CopyOnWriteArrayList<>();
    
    // Mapa de jugadores con física
    private final Map<PlayerId, PlayerPhysics> playerPhysics = new HashMap<>();
    private final Map<PlayerId, Player> players = new HashMap<>();
    
    private final Queue<MoveCommand> moves = new ConcurrentLinkedQueue<>();
    private Speed speed = new Speed("1");
    
    // Nivel (plataformas y lianas)
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Liana> lianas = new ArrayList<>();

    public Game(final GameEventBus bus, final DefaultEntityFactory factory){
        this.bus = Objects.requireNonNull(bus);
        this.factory = Objects.requireNonNull(factory);
        initLevel();
    }
    
    private void initLevel() {
        // Plataformas (mismo layout que en cliente)
        platforms.add(new Platform(50, 520, 700, 20));      // piso
        platforms.add(new Platform(80, 420, 200, 15));      // nivel 1
        platforms.add(new Platform(520, 420, 200, 15));
        platforms.add(new Platform(50, 320, 180, 15));      // nivel 2
        platforms.add(new Platform(310, 320, 180, 15));
        platforms.add(new Platform(570, 320, 180, 15));
        platforms.add(new Platform(130, 220, 200, 15));     // nivel 3
        platforms.add(new Platform(470, 220, 200, 15));
        platforms.add(new Platform(200, 120, 400, 15));     // nivel 4
        platforms.add(new Platform(300, 40, 200, 20));      // meta (DK)
        
        // Lianas
        for (int i = 0; i < 6; i++) {
            float x = 100.0f + i * 120.0f;
            lianas.add(new Liana(x, 30.0f, 540.0f));
        }
    }

    // ===== API ADMIN =====
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
        PlayerPhysics phys = new PlayerPhysics();
        playerPhysics.put(id, phys);
        
        // Player con posición lógica (legacy, ya no se usa mucho)
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }

    public void removePlayer(final PlayerId id){
        playerPhysics.remove(id);
        players.remove(id);
        emitState();
    }

    // ===== MOVIMIENTOS =====
    public void enqueueMove(final PlayerId id, final Direction dir){
        if (id == null || dir == null) return;
        moves.offer(new MoveCommand(id, dir));
    }

    // ===== LOOP (cada ~50ms para 60 FPS simulado) =====
    public void step(){
        float dt = 0.05f; // 50ms
        
        processMoves(dt);
        updatePhysics(dt);
        
        // Mover cocodrilos
        for (var r : reds)  r.step();
        for (var b : blues) b.step();
        
        emitState();
    }

    private void processMoves(float dt){
        MoveCommand m;
        while ((m = moves.poll()) != null) {
            var phys = playerPhysics.get(m.player());
            if (phys == null) continue;

            switch (m.dir()) {
                case LEFT -> {
                    if (!phys.onLiana) {
                        phys.vx = -GameRules.MOVE_SPEED;
                    }
                }
                case RIGHT -> {
                    if (!phys.onLiana) {
                        phys.vx = GameRules.MOVE_SPEED;
                    }
                }
                case UP -> {
                    if (phys.onLiana) {
                        // Trepar
                        phys.vy = -GameRules.CLIMB_SPEED;
                    } else {
                        // Intentar agarrar liana
                        for (int i = 0; i < lianas.size(); i++) {
                            if (lianas.get(i).canGrab(phys.x, phys.y, 30.0f)) {
                                phys.onLiana = true;
                                phys.lianaIndex = i;
                                phys.x = lianas.get(i).x; // snap a la liana
                                phys.vx = 0;
                                phys.vy = 0;
                                break;
                            }
                        }
                    }
                }
                case DOWN -> {
                    if (phys.onLiana) {
                        phys.vy = GameRules.CLIMB_SPEED;
                    }
                }
                case JUMP -> {
                    if (phys.onGround && !phys.onLiana) {
                        phys.vy = -GameRules.JUMP_FORCE;
                        phys.onGround = false;
                    } else if (phys.onLiana) {
                        // Soltar liana con salto
                        phys.onLiana = false;
                        phys.vy = -GameRules.JUMP_FORCE * 0.5f;
                    }
                }
            }
        }
    }
    
    private void updatePhysics(float dt) {
        for (var phys : playerPhysics.values()) {
            if (phys.onLiana) {
                // En liana: solo movimiento vertical controlado
                phys.y += phys.vy * dt;
                
                // Limitar a la liana
                if (phys.lianaIndex >= 0 && phys.lianaIndex < lianas.size()) {
                    Liana liana = lianas.get(phys.lianaIndex);
                    if (phys.y < liana.topY) phys.y = liana.topY;
                    if (phys.y > liana.bottomY) {
                        phys.y = liana.bottomY;
                        phys.onLiana = false;
                    }
                }
                
                phys.vy = 0;
            } else {
                // Física normal (plataformas)
                
                // Aplicar gravedad si no está en el suelo
                if (!phys.onGround) {
                    phys.vy += GameRules.GRAVITY * dt;
                }
                
                // Aplicar velocidad
                phys.x += phys.vx * dt;
                phys.y += phys.vy * dt;
                
                // Fricción horizontal
                phys.vx *= 0.85f;
                if (Math.abs(phys.vx) < 5.0f) phys.vx = 0;
                
                // Colisiones con plataformas (MEJORADO)
                phys.onGround = false;
                
                final float PLAYER_WIDTH = 20.0f;
                final float PLAYER_HEIGHT = 30.0f;
                
                for (Platform p : platforms) {
                    // Verificar si el jugador está cayendo sobre la plataforma
                    if (phys.vy >= 0) { // solo si cae o está quieto
                        float playerBottom = phys.y + PLAYER_HEIGHT;
                        float playerLeft = phys.x - PLAYER_WIDTH / 2;
                        float playerRight = phys.x + PLAYER_WIDTH / 2;
                        
                        // Verificar overlap horizontal
                        boolean overlapX = playerRight > p.x && playerLeft < p.x + p.w;
                        
                        // Verificar si los pies están cerca de la plataforma
                        if (overlapX && playerBottom >= p.y && playerBottom <= p.y + 10.0f) {
                            phys.onGround = true;
                            phys.y = p.y - PLAYER_HEIGHT; // Ajustar para que quede arriba
                            phys.vy = 0;
                            break;
                        }
                    }
                }
                
                // Límites del mundo
                if (phys.x < GameRules.MIN_X) phys.x = GameRules.MIN_X;
                if (phys.x > GameRules.MAX_X) phys.x = GameRules.MAX_X;
                
                // Si cae muy abajo, resetear al piso
                if (phys.y > GameRules.MAX_Y) {
                    phys.y = 500.0f; // piso principal
                    phys.x = 100.0f;
                    phys.vy = 0;
                    phys.onGround = true;
                }
            }
        }
    }

    // ===== SERIALIZACIÓN =====
    private void emitState(){ 
        bus.emit(new StateEvent(snapshot())); 
    }

    public String snapshot(){
        var playersTxt = new StringBuilder();
        for (var entry : playerPhysics.entrySet()) {
            var id = entry.getKey();
            var phys = entry.getValue();
            
            if (playersTxt.length() > 0) playersTxt.append("|");
            playersTxt.append("id=").append(id.value())
                      .append(",x=").append(String.format("%.1f", phys.x))
                      .append(",y=").append(String.format("%.1f", phys.y))
                      .append(",onLiana=").append(phys.onLiana ? "1" : "0");
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