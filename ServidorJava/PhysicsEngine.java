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
            // Movimiento en liana (sin cambios)
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

                Rect pr = playerRect(phys.x, phys.y);

                for (CrocodileRed rc : level.crocodileReds()) {
                    try {
                        float cx = level.xOf(rc.position().liana());
                        int logicalH = Integer.parseInt(rc.position().height().value());
                        float cy = heightToPixels(logicalH);

                        if (rectOverlap(pr, crocRect(cx, cy, true))) {
                            System.out.println("[COLLISION ON LIANA] RED CROC HIT!");
                            respawn(phys);
                            phys.markRespawned();
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR RED ON LIANA] " + e.getMessage());
                    }
                }

                for (CrocodileBlue bc : level.crocodileBlues()) {
                    try {
                        float cx = level.xOf(bc.position().liana());
                        int logicalH = Integer.parseInt(bc.position().height().value());
                        float cy = heightToPixels(logicalH);

                        if (rectOverlap(pr, crocRect(cx, cy, false))) {
                            System.out.println("[COLLISION ON LIANA] BLUE CROC HIT!");
                            respawn(phys);
                            phys.markRespawned();
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR BLUE ON LIANA] " + e.getMessage());
                    }
                }

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

            // GRAVEDAD + INTEGRACION + TERMINAL VELOCITY
            if (!phys.onGround) {
                phys.vy += GameRules.GRAVITY * dt;
                
                // Limitar velocidad de caida (terminal velocity)
                if (phys.vy > GameRules.MAX_FALL_SPEED) {
                    phys.vy = GameRules.MAX_FALL_SPEED;
                }
            }

            // Guardamos posicion previa para swept (ahora PREV X y PREV Y)
            float prevX = phys.x;
            float prevY = phys.y;

            // Aplicamos integracion
            phys.x += phys.vx * dt;
            phys.y += phys.vy * dt;

            // Friccion
            phys.vx *= 0.85f;
            if (Math.abs(phys.vx) < 5.0f) phys.vx = 0;

            // Suelo: asumimos por defecto que no esta en ground (se recalculara)
            phys.onGround = false;

            // Coordenadas del AABB del jugador (actual y previas)
            float playerLeft   = phys.x - PLAYER_WIDTH  * 0.5f;
            float playerRight  = phys.x + PLAYER_WIDTH  * 0.5f;
            float playerTop    = phys.y - PLAYER_HEIGHT * 0.5f;
            float playerBottom = phys.y + PLAYER_HEIGHT * 0.5f;

            float prevLeft   = prevX - PLAYER_WIDTH  * 0.5f;
            float prevRight  = prevX + PLAYER_WIDTH  * 0.5f;
            float prevTop    = prevY - PLAYER_HEIGHT * 0.5f;
            float prevBottom = prevY + PLAYER_HEIGHT * 0.5f;

            // Tolerancias (ajustables)
            final float SNAP_TOLERANCE = 15.0f;  // para aterrizaje desde arriba
            final float SIDE_TOLERANCE = 1.0f;   // evitar quedarse "pegado" por float rounding

            for (Platform p : platforms) {
                // Primero: comprobaciones de solapamiento en proyeccion X/Y para decidir que eje colisiono
                boolean overlapX_now = playerRight > p.x && playerLeft < p.x + p.w;
                boolean overlapY_now = playerBottom > p.y && playerTop < p.y + p.h;

                // Si no hay superposicion en absoluto, siguiente plataforma
                if (!overlapX_now && !overlapY_now) continue;

                // Colisiones verticales (techo / suelo)
                // Aterrizaje desde arriba (prevBottom <= p.y && currBottom >= p.y)
                if (prevBottom <= p.y && playerBottom >= p.y && overlapX_now) {
                    float distToSurface = playerBottom - p.y;
                    if (distToSurface <= SNAP_TOLERANCE) {
                        // Snap hacia arriba (landing)
                        phys.onGround = true;
                        phys.y = p.y - (PLAYER_HEIGHT * 0.5f);
                        phys.vy = 0;
                    }
                }
                // Golpe con el techo (prevTop >= p.y + p.h && currTop <= p.y + p.h)
                else if (prevTop >= p.y + p.h && playerTop <= p.y + p.h && overlapX_now) {
                    // poner al jugador justo debajo de la plataforma (colision con la cara inferior)
                    phys.y = (p.y + p.h) + (PLAYER_HEIGHT * 0.5f);
                    phys.vy = 0;
                }

                // Colisiones horizontales (laterales)
                // Colision desde la izquierda (prevRight <= p.x && currRight >= p.x)
                if (prevRight <= p.x && playerRight >= p.x) {
                    if (playerBottom > p.y + SIDE_TOLERANCE && playerTop < p.y + p.h - SIDE_TOLERANCE) {
                        phys.x = p.x - PLAYER_WIDTH * 0.5f - 0.01f;
                        phys.vx = 0;
                        playerLeft  = phys.x - PLAYER_WIDTH  * 0.5f;
                        playerRight = phys.x + PLAYER_WIDTH  * 0.5f;
                    }
                }
                // Colision desde la derecha (prevLeft >= p.x + p.w && currLeft <= p.x + p.w)
                else if (prevLeft >= p.x + p.w && playerLeft <= p.x + p.w) {
                    if (playerBottom > p.y + SIDE_TOLERANCE && playerTop < p.y + p.h - SIDE_TOLERANCE) {
                        phys.x = p.x + p.w + PLAYER_WIDTH * 0.5f + 0.01f;
                        phys.vx = 0;
                        playerLeft  = phys.x - PLAYER_WIDTH  * 0.5f;
                        playerRight = phys.x + PLAYER_WIDTH  * 0.5f;
                    }
                }
            }

            // Limites & caida
            if (phys.x < GameRules.MIN_X) phys.x = GameRules.MIN_X;
            if (phys.x > GameRules.MAX_X) phys.x = GameRules.MAX_X;
            if (phys.y > GameRules.MAX_Y) respawn(phys);

            // COLISIONES CON ENTIDADES (todas centradas)
            Rect pr = playerRect(phys.x, phys.y);

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

                    Rect fr = fruitRect(fx, fy);
                    boolean overlap = rectOverlap(pr, fr);

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
        System.out.println("════════════════════════════════════════");
        System.out.println("[RESPAWN DEBUG] Player died!");
        System.out.println("  Position: x=" + phys.x + ", y=" + phys.y);
        System.out.println("  Velocity: vx=" + phys.vx + ", vy=" + phys.vy);
        System.out.println("  onLiana: " + phys.onLiana + ", onGround: " + phys.onGround);
        System.out.println("  Bounds check:");
        System.out.println("    MIN_X=" + GameRules.MIN_X + " (is x < MIN_X? " + (phys.x < GameRules.MIN_X) + ")");
        System.out.println("    MAX_X=" + GameRules.MAX_X + " (is x > MAX_X? " + (phys.x > GameRules.MAX_X) + ")");
        System.out.println("    MAX_Y=" + GameRules.MAX_Y + " (is y > MAX_Y? " + (phys.y > GameRules.MAX_Y) + ")");
        System.out.println("════════════════════════════════════════");
        
        phys.x = 150.0f;
        phys.y = 490.0f;
        phys.vy = 0;
        phys.onGround = true;
        phys.onLiana  = false;
        phys.lianaIndex = -1;
    }

    private static float heightToPixels(int logicalHeight) {
        float min_y = 30.0f;
        float max_y = 540.0f;
        float range = max_y - min_y;
        return max_y - (logicalHeight / 12.0f) * range;
    }

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