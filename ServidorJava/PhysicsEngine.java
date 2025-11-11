import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

final class PhysicsEngine {
    private static final float PLAYER_WIDTH  = 20.0f;
    private static final float PLAYER_HEIGHT = 30.0f;

    private final Level level;
    private final Map<PlayerId, PlayerPhysics> playerPhysics = new HashMap<>();
    private final Queue<MoveCommand> moves = new ConcurrentLinkedQueue<>();

    PhysicsEngine(Level level) {
        this.level = Objects.requireNonNull(level);
    }

    Map<PlayerId, PlayerPhysics> getPlayerPhysics() {
        return playerPhysics;
    }

    void addPlayer(PlayerId id) {
        playerPhysics.put(id, new PlayerPhysics());
    }

    void removePlayer(PlayerId id) {
        playerPhysics.remove(id);
    }

    void enqueueMove(PlayerId id, Direction dir) {
        if (id == null || dir == null) return;
        moves.offer(new MoveCommand(id, dir));
    }

    void update(float dt) {
        processMoves(dt);
        updatePhysics(dt);
    }

    private void processMoves(float dt) {
        MoveCommand m;
        while ((m = moves.poll()) != null) {
            var phys = playerPhysics.get(m.player());
            if (phys == null) continue;

            switch (m.dir()) {
                case LEFT -> { if (!phys.onLiana) phys.vx = -GameRules.MOVE_SPEED; }
                case RIGHT -> { if (!phys.onLiana) phys.vx =  GameRules.MOVE_SPEED; }
                case UP -> {
                    if (phys.onLiana) {
                        phys.vy = -GameRules.CLIMB_SPEED;
                    } else {
                        tryGrabLiana(phys);
                    }
                }
                case DOWN -> {
                    if (phys.onLiana) phys.vy = GameRules.CLIMB_SPEED;
                }
                case JUMP -> {
                    if (phys.onGround && !phys.onLiana) {
                        phys.vy = -GameRules.JUMP_FORCE;
                        phys.onGround = false;
                    } else if (phys.onLiana) {
                        phys.onLiana = false;
                        phys.vy = -GameRules.JUMP_FORCE * 0.5f;
                    }
                }
            }
        }
    }

    private void updatePhysics(float dt) {
        final var platforms = level.platforms();
        final var lianas    = level.lianas();

        for (var phys : playerPhysics.values()) {
            // Movimiento en liana
            if (phys.onLiana) {
                phys.y += phys.vy * dt;

                if (phys.lianaIndex >= 0 && phys.lianaIndex < lianas.size()) {
                    Liana liana = lianas.get(phys.lianaIndex);
                    if (phys.y < liana.topY) phys.y = liana.topY;
                    if (phys.y > liana.bottomY) {
                        phys.y = liana.bottomY;
                        phys.onLiana = false;
                    }
                }
                phys.vy = 0;
                
                // ⭐ DETECCIÓN DE FRUTAS EN LIANA (antes del continue)
                Rect pr = playerRect(phys.x, phys.y);
                for (Fruit f : level.fruits()) {
                    try {
                        if (f.isCollected()) continue;
                        
                        float fx = level.xOf(f.position().liana());
                        int logicalH = Integer.parseInt(f.position().height().value());
                        float fy = heightToPixels(logicalH);
                        
                        Rect fr = fruitRect(fx, fy);
                        boolean overlap = rectOverlap(pr, fr);
                        
                        if (overlap) {
                            System.out.println("[COLLISION ON LIANA] FRUIT COLLECTED! Points: " + f.points().value());
                            f.setCollected(true);
                            int pts = Integer.parseInt(f.points().value());
                            phys.addScore(pts);
                            System.out.println("[SCORE] New score: " + phys.score);
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR FRUIT ON LIANA] " + e.getMessage());
                    }
                }
                
                continue;
            }

            // Gravedad + integración
            if (!phys.onGround) phys.vy += GameRules.GRAVITY * dt;
            phys.x += phys.vx * dt;
            phys.y += phys.vy * dt;

            // Fricción
            phys.vx *= 0.85f;
            if (Math.abs(phys.vx) < 5.0f) phys.vx = 0;

            // Suelo: usando y como CENTRO
            phys.onGround = false;
            if (phys.vy >= 0) {
                float playerBottom = phys.y + PLAYER_HEIGHT * 0.5f;
                float playerLeft   = phys.x - PLAYER_WIDTH  * 0.5f;
                float playerRight  = phys.x + PLAYER_WIDTH  * 0.5f;

                for (Platform p : platforms) {
                    boolean overlapX = playerRight > p.x && playerLeft < p.x + p.w;
                    if (overlapX && playerBottom >= p.y && playerBottom <= p.y + 10.0f) {
                        phys.onGround = true;
                        phys.y = p.y - (PLAYER_HEIGHT * 0.5f);
                        phys.vy = 0;
                        break;
                    }
                }
            }

            // Límites & caída
            if (phys.x < GameRules.MIN_X) phys.x = GameRules.MIN_X;
            if (phys.x > GameRules.MAX_X) phys.x = GameRules.MAX_X;
            if (phys.y > GameRules.MAX_Y) respawn(phys);

            // ===== COLISIONES CON ENTIDADES (todas centradas) =====
            Rect pr = playerRect(phys.x, phys.y);

            // 🔍 DEBUG cada segundo
            if (System.currentTimeMillis() % 1000 < 50) {
                System.out.println("[DEBUG] Player: x=" + phys.x + ", y=" + phys.y);
                System.out.println("[DEBUG] Reds count: " + level.crocodileReds().size());
                System.out.println("[DEBUG] Blues count: " + level.crocodileBlues().size());
                System.out.println("[DEBUG] Fruits count: " + level.fruits().size());
            }

            // Rojos
            for (CrocodileRed rc : level.crocodileReds()) {
                try {
                    float cx = level.xOf(rc.position().liana());
                    int logicalH = Integer.parseInt(rc.position().height().value());
                    float cy = heightToPixels(logicalH);
                    
                    if (rectOverlap(pr, crocRect(cx, cy, true))) {
                        System.out.println("[COLLISION] RED CROC HIT!");
                        respawn(phys);
                        phys.markRespawned();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR RED] " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Azules
            for (CrocodileBlue bc : level.crocodileBlues()) {
                try {
                    float cx = level.xOf(bc.position().liana());
                    int logicalH = Integer.parseInt(bc.position().height().value());
                    float cy = heightToPixels(logicalH);
                    
                    if (rectOverlap(pr, crocRect(cx, cy, false))) {
                        System.out.println("[COLLISION] BLUE CROC HIT!");
                        respawn(phys);
                        phys.markRespawned();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR BLUE] " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Frutas
            for (Fruit f : level.fruits()) {
                try {
                    if (f.isCollected()) continue;
                    
                    float fx = level.xOf(f.position().liana());
                    int logicalH = Integer.parseInt(f.position().height().value());
                    float fy = heightToPixels(logicalH);
                    
                    // Debug cuando estás cerca
                    float distX = Math.abs(phys.x - fx);
                    float distY = Math.abs(phys.y - fy);
                    
                    if (distX < 100 && distY < 100) {
                        System.out.println("[FRUIT NEAR] Player(" + phys.x + "," + phys.y + ") Fruit(" + fx + "," + fy + ") logical=" + logicalH);
                        System.out.println("[FRUIT NEAR] Distance: x=" + distX + " y=" + distY);
                    }
                    
                    Rect fr = fruitRect(fx, fy);
                    boolean overlap = rectOverlap(pr, fr);
                    
                    if (distX < 50 && distY < 50) {
                        System.out.println("[FRUIT DETAIL] Player rect: x=" + pr.x + " y=" + pr.y + " w=" + pr.w + " h=" + pr.h);
                        System.out.println("[FRUIT DETAIL] Fruit rect: x=" + fr.x + " y=" + fr.y + " w=" + fr.w + " h=" + fr.h);
                        System.out.println("[FRUIT DETAIL] Overlap: " + overlap);
                    }
                    
                    if (overlap) {
                        System.out.println("[COLLISION] FRUIT COLLECTED! Points: " + f.points().value());
                        f.setCollected(true);
                        int pts = Integer.parseInt(f.points().value());
                        phys.addScore(pts);
                        System.out.println("[SCORE] New score: " + phys.score);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR FRUIT] " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void tryGrabLiana(PlayerPhysics phys) {
        var lianas = level.lianas();
        for (int i = 0; i < lianas.size(); i++) {
            if (lianas.get(i).canGrab(phys.x, phys.y, 30.0f)) {
                phys.onLiana   = true;
                phys.lianaIndex = i;
                phys.x = lianas.get(i).x;
                phys.vx = 0;
                phys.vy = 0;
                break;
            }
        }
    }

    private void respawn(PlayerPhysics phys) {
        System.out.println("[RESPAWN] Player died!");
        phys.x = 150.0f;
        phys.y = 490.0f;
        phys.vy = 0;
        phys.onGround = true;
        phys.onLiana  = false;
        phys.lianaIndex = -1;
    }

    // ⭐ Conversión de altura lógica (0-12) a píxeles (540-30)
    private static float heightToPixels(int logicalHeight) {
        // Altura lógica 0 = piso (y=540)
        // Altura lógica 12 = tope (y=30)
        float min_y = 30.0f;
        float max_y = 540.0f;
        float range = max_y - min_y;  // 510
        return max_y - (logicalHeight / 12.0f) * range;
    }

    // ======== HITBOXES centrados ========
    static final class Rect {
        final float x, y, w, h;
        Rect(float x, float y, float w, float h){ this.x=x; this.y=y; this.w=w; this.h=h; }
    }

    static boolean rectOverlap(Rect a, Rect b){
        return a.x < b.x + b.w && a.x + a.w > b.x &&
               a.y < b.y + b.h && a.y + a.h > b.y;
    }

    private static Rect playerRect(float x, float y){
        return new Rect(x - PLAYER_WIDTH*0.5f, y - PLAYER_HEIGHT*0.5f,
                        PLAYER_WIDTH, PLAYER_HEIGHT);
    }

    private static Rect crocRect(float x, float y, boolean isRed) {
        float w = isRed ? 26f : 22f;
        float h = isRed ? 22f : 18f;
        return new Rect(x - w/2, y - h/2, w, h);
    }

    private static Rect fruitRect(float x, float y) {
        float w = 14f, h = 14f;
        return new Rect(x - w/2, y - h/2, w, h);
    }
}