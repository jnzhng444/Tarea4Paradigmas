
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

    // ====== Internals ======
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
                        // Intentar agarrar liana
                        var lianas = level.lianas();
                        for (int i = 0; i < lianas.size(); i++) {
                            if (lianas.get(i).canGrab(phys.x, phys.y, 30.0f)) {
                                phys.onLiana = true;
                                phys.lianaIndex = i;
                                phys.x = lianas.get(i).x; // snap
                                phys.vx = 0;
                                phys.vy = 0;
                                break;
                            }
                        }
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
                        // Soltar liana con “saltito”
                        phys.onLiana = false;
                        phys.vy = -GameRules.JUMP_FORCE * 0.5f;
                    }
                }
            }
        }
    }

    private void updatePhysics(float dt) {
        final var platforms = level.platforms();
        final var lianas = level.lianas();

        for (var phys : playerPhysics.values()) {
            if (phys.onLiana) {
                // Movimiento vertical controlado
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
                continue;
            }

            // Gravedad
            if (!phys.onGround) phys.vy += GameRules.GRAVITY * dt;

            // Integración
            phys.x += phys.vx * dt;
            phys.y += phys.vy * dt;

            // Fricción horizontal
            phys.vx *= 0.85f;
            if (Math.abs(phys.vx) < 5.0f) phys.vx = 0;

            // Colisiones (suelo/plataformas)
            phys.onGround = false;

            if (phys.vy >= 0) { // cayendo o quieto
                float playerBottom = phys.y + PLAYER_HEIGHT;
                float playerLeft   = phys.x - PLAYER_WIDTH / 2;
                float playerRight  = phys.x + PLAYER_WIDTH / 2;

                for (Platform p : platforms) {
                    boolean overlapX = playerRight > p.x && playerLeft < p.x + p.w;
                    if (overlapX && playerBottom >= p.y && playerBottom <= p.y + 10.0f) {
                        phys.onGround = true;
                        phys.y = p.y - PLAYER_HEIGHT;
                        phys.vy = 0;
                        break;
                    }
                }
            }

            // Límites del mundo
            if (phys.x < GameRules.MIN_X) phys.x = GameRules.MIN_X;
            if (phys.x > GameRules.MAX_X) phys.x = GameRules.MAX_X;

            // Caída fuera
            if (phys.y > GameRules.MAX_Y) {
                phys.y = 500.0f; // piso principal
                phys.x = 100.0f;
                phys.vy = 0;
                phys.onGround = true;
            }
        }
    }
}
