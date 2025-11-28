package com.dkj.game;

import com.dkj.commands.MoveCommand;
import com.dkj.entities.CrocodileBlue;
import com.dkj.entities.CrocodileRed;
import com.dkj.entities.Fruit;
import com.dkj.model.Direction;
import com.dkj.model.Liana;
import com.dkj.model.Platform;
import com.dkj.model.PlayerId;
import com.dkj.model.PlayerPhysics;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Motor de fisica encargado de procesar movimientos, colisiones y reapariciones
 * de los jugadores.
 */
final class PhysicsEngine {
    private static final Float PLAYER_WIDTH  = Float.valueOf(20.0f);
    private static final Float PLAYER_HEIGHT = Float.valueOf(30.0f);

    private final Level level;
    private final Map<PlayerId, PlayerPhysics> playerPhysics = new HashMap<>();
    private final Queue<MoveCommand> moves = new ConcurrentLinkedQueue<>();

    /**
     * Crea el motor de fisica apoyado en la geometria del {@link Level} dado.
     *
     * @param level nivel sobre el cual se simularan las interacciones
     */
    PhysicsEngine(Level level) {
        this.level = Objects.requireNonNull(level);
    }

    /**
     * Obtiene el mapa completo de estados fisicos por jugador.
     *
     * @return mapa inmutable con la fisica de cada jugador
     */
    Map<PlayerId, PlayerPhysics> getPlayerPhysics() {
        return playerPhysics;
    }
    
    /**
     * Recupera el estado fisico asociado al jugador indicado.
     *
     * @param id jugador requerido
     * @return instancia {@link PlayerPhysics} o {@code null} si no existe
     */
    PlayerPhysics getPhysicsFor(PlayerId id) {
        return playerPhysics.get(id);
    }

    /**
     * Agrega un jugador inicializando su estado fisico por defecto.
     *
     * @param id identificador del nuevo jugador
     */
    void addPlayer(PlayerId id) {
        playerPhysics.put(id, new PlayerPhysics());
    }

    /**
     * Elimina el estado fisico asociado al jugador dado.
     *
     * @param id jugador a retirar
     */
    void removePlayer(PlayerId id) {
        playerPhysics.remove(id);
    }

    /**
     * Encola una orden de movimiento para ser procesada en el siguiente tick.
     *
     * @param id jugador emisor del comando
     * @param dir direccion solicitada
     */
    void enqueueMove(PlayerId id, Direction dir) {
        if (id == null || dir == null) return;
        moves.offer(new MoveCommand(id, dir));
    }

    /**
     * Avanza la simulacion del motor aplicando movimientos y fisica.
     *
     * @param dt delta de tiempo en segundos
     */
    void update(Float dt) {
        processMoves(dt);
        updatePhysics(dt);
    }

    /**
     * Procesa la cola de comandos de movimiento ajustando las velocidades.
     *
     * @param dt delta de tiempo actual (algunos movimientos lo utilizan)
     */
    private void processMoves(Float dt) {
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
                        phys.onGround = Boolean.FALSE;
                    } else if (phys.onLiana) {
                        phys.onLiana = Boolean.FALSE;
                        phys.vy = -GameRules.JUMP_FORCE * 0.5f;
                    }
                }
            }
        }
    }

    /**
     * Actualiza la dinamica de los jugadores aplicando gravedad, colisiones y
     * deteccion de interacciones con entidades.
     *
     * @param dt delta de tiempo en segundos
     */
    private void updatePhysics(Float dt) {
        final var platforms = level.platforms();
        final var lianas    = level.lianas();

        for (var phys : playerPhysics.values()) {
            // Movimiento en liana (sin cambios)
            if (phys.onLiana) {
                phys.y += phys.vy * dt;

                if (phys.lianaIndex >= Integer.valueOf(0) && phys.lianaIndex < lianas.size()) {
                    Liana liana = lianas.get(phys.lianaIndex);
                    // Solo para la mini liana (índice 6, ID "7"), permitir subir 20 píxeles extra
                    Float allowedTopY = (phys.lianaIndex == Integer.valueOf(6)) 
                        ? liana.topY - Float.valueOf(20.0f) 
                        : liana.topY;
                    if (phys.y < allowedTopY) phys.y = allowedTopY;
                    if (phys.y > liana.bottomY) {
                        phys.y = liana.bottomY;
                        phys.onLiana = Boolean.FALSE;
                    }
                }
                phys.vy = Float.valueOf(0);

                Rect pr = playerRect(phys.x, phys.y);
                
                // Colisión con Mario
                Float marioX = level.marioX();
                Float marioY = level.marioY();
                Rect marioRect = new Rect(marioX - Float.valueOf(20.0f), marioY - Float.valueOf(20.0f), 
                                         Float.valueOf(40.0f), Float.valueOf(40.0f));
                if (rectOverlap(pr, marioRect)) {
                    System.out.println("[COLLISION] Player touched Mario! Respawning...");
                    respawn(phys);
                    phys.markRespawned();
                }

                for (CrocodileRed rc : level.crocodileReds()) {
                    try {
                        Float cx = level.xOf(rc.position().liana());
                        Integer logicalH = Integer.parseInt(rc.position().height().value());
                        Float cy = heightToPixels(logicalH);

                        if (rectOverlap(pr, crocRect(cx, cy, Boolean.TRUE))) {
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
                        Float cx, cy;
                        Rect blueRect;
                        
                        // Si está caminando en plataforma (liana "0")
                        if (bc.position().liana().value().equals("0")) {
                            cx = bc.getPlatformX();
                            Integer logicalH = Integer.parseInt(bc.position().height().value());
                            cy = heightToPixels(logicalH);
                            blueRect = crocBlueWalkingRect(cx, cy);  // Hitbox horizontal
                        } 
                        // Si está bajando en una liana
                        else {
                            cx = level.xOf(bc.position().liana());
                            Integer logicalH = Integer.parseInt(bc.position().height().value());
                            cy = heightToPixels(logicalH);
                            blueRect = crocBlueDescendingRect(cx, cy);  // Hitbox vertical
                        }

                        if (rectOverlap(pr, blueRect)) {
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

                        Float fx = level.xOf(f.position().liana());
                        Integer logicalH = Integer.parseInt(f.position().height().value());
                        Float fy = heightToPixels(logicalH);

                        Rect fr = fruitRect(fx, fy);
                        Boolean overlap = rectOverlap(pr, fr);

                        if (overlap) {
                            System.out.println("[COLLISION ON LIANA] FRUIT COLLECTED! Points: " + f.points().value());
                            f.setCollected(Boolean.TRUE);
                            Integer pts = Integer.parseInt(f.points().value());
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

            // Guardamos posicion previa para swept
            Float prevX = phys.x;
            Float prevY = phys.y;

            // Aplicamos integracion
            phys.x += phys.vx * dt;
            phys.y += phys.vy * dt;

            // Friccion
            phys.vx *= 0.85f;
            if (Math.abs(phys.vx) < 5.0f) phys.vx = Float.valueOf(0);

            // Suelo: asumimos por defecto que no esta en ground
            phys.onGround = Boolean.FALSE;

            // Coordenadas del AABB del jugador (actual y previas)
            Float playerLeft   = phys.x - PLAYER_WIDTH  * 0.5f;
            Float playerRight  = phys.x + PLAYER_WIDTH  * 0.5f;
            Float playerTop    = phys.y - PLAYER_HEIGHT * 0.5f;
            Float playerBottom = phys.y + PLAYER_HEIGHT * 0.5f;

            Float prevLeft   = prevX - PLAYER_WIDTH  * 0.5f;
            Float prevRight  = prevX + PLAYER_WIDTH  * 0.5f;
            Float prevTop    = prevY - PLAYER_HEIGHT * 0.5f;
            Float prevBottom = prevY + PLAYER_HEIGHT * 0.5f;

            // Tolerancia
            final Float SIDE_TOLERANCE = Float.valueOf(1.0f);

            for (Platform p : platforms) {
                // Comprobaciones de solapamiento
                Boolean overlapX_now = playerRight > p.x && playerLeft < p.x + p.w;
                Boolean overlapX_prev = prevRight > p.x && prevLeft < p.x + p.w;

                // ===== COLISIÓN VERTICAL (TECHO / SUELO) =====
                
                // ATERRIZAJE DESDE ARRIBA - Swept Collision Detection
                if (prevBottom <= p.y && playerBottom >= p.y && overlapX_prev) {
                    // Posicionar exactamente sobre la plataforma
                    phys.onGround = Boolean.TRUE;
                    phys.y = p.y - (PLAYER_HEIGHT * 0.5f);
                    phys.vy = Float.valueOf(0);
                    
                    // Actualizar coordenadas del AABB después del ajuste
                    playerTop = phys.y - PLAYER_HEIGHT * 0.5f;
                    playerBottom = phys.y + PLAYER_HEIGHT * 0.5f;
                }
                // Golpe con el techo
                else if (prevTop >= p.y + p.h && playerTop <= p.y + p.h && overlapX_now) {
                    phys.y = (p.y + p.h) + (PLAYER_HEIGHT * 0.5f);
                    phys.vy = Float.valueOf(0);
                }

                // ===== COLISIONES HORIZONTALES (LATERALES) =====
                // Colision desde la izquierda
                if (prevRight <= p.x && playerRight >= p.x) {
                    if (playerBottom > p.y + SIDE_TOLERANCE && playerTop < p.y + p.h - SIDE_TOLERANCE) {
                        phys.x = p.x - PLAYER_WIDTH * 0.5f - 0.01f;
                        phys.vx = Float.valueOf(0);
                        playerLeft  = phys.x - PLAYER_WIDTH  * 0.5f;
                        playerRight = phys.x + PLAYER_WIDTH  * 0.5f;
                    }
                }
                // Colision desde la derecha
                else if (prevLeft >= p.x + p.w && playerLeft <= p.x + p.w) {
                    if (playerBottom > p.y + SIDE_TOLERANCE && playerTop < p.y + p.h - SIDE_TOLERANCE) {
                        phys.x = p.x + p.w + PLAYER_WIDTH * 0.5f + 0.01f;
                        phys.vx = Float.valueOf(0);
                        playerLeft  = phys.x - PLAYER_WIDTH  * 0.5f;
                        playerRight = phys.x + PLAYER_WIDTH  * 0.5f;
                    }
                }
            }

            // Limites & caida
            if (phys.x < GameRules.MIN_X) phys.x = GameRules.MIN_X;
            if (phys.x > GameRules.MAX_X) phys.x = GameRules.MAX_X;
            if (phys.y > GameRules.MAX_Y) {
                respawn(phys);
                phys.markRespawned();
            }

            // COLISIONES CON ENTIDADES
            Rect pr = playerRect(phys.x, phys.y);
            
            // Colisión con Mario
            Float marioX = level.marioX();
            Float marioY = level.marioY();
            Rect marioRect = new Rect(marioX - Float.valueOf(20.0f), marioY - Float.valueOf(20.0f), 
                                     Float.valueOf(40.0f), Float.valueOf(40.0f));
            if (rectOverlap(pr, marioRect)) {
                System.out.println("[COLLISION] Player touched Mario! Respawning...");
                respawn(phys);
                phys.markRespawned();
            }

            // Rojos
            for (CrocodileRed rc : level.crocodileReds()) {
                try {
                    Float cx = level.xOf(rc.position().liana());
                    Integer logicalH = Integer.parseInt(rc.position().height().value());
                    Float cy = heightToPixels(logicalH);

                    if (rectOverlap(pr, crocRect(cx, cy, Boolean.TRUE))) {
                        System.out.println("[COLLISION] RED CROC HIT!");
                        respawn(phys);
                        phys.markRespawned();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR RED] " + e.getMessage());
                }
            }

            // Azules
            for (CrocodileBlue bc : level.crocodileBlues()) {
                try {
                    Float cx, cy;
                    Rect blueRect;
                    
                    // Si está caminando en plataforma (liana "0")
                    if (bc.position().liana().value().equals("0")) {
                        cx = bc.getPlatformX();
                        Integer logicalH = Integer.parseInt(bc.position().height().value());
                        cy = heightToPixels(logicalH);
                        blueRect = crocBlueWalkingRect(cx, cy);  // Hitbox horizontal
                    } 
                    // Si está bajando en una liana
                    else {
                        cx = level.xOf(bc.position().liana());
                        Integer logicalH = Integer.parseInt(bc.position().height().value());
                        cy = heightToPixels(logicalH);
                        blueRect = crocBlueDescendingRect(cx, cy);  // Hitbox vertical
                    }

                    if (rectOverlap(pr, blueRect)) {
                        System.out.println("[COLLISION] BLUE CROC HIT!");
                        respawn(phys);
                        phys.markRespawned();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR BLUE] " + e.getMessage());
                }
            }

            // Frutas
            for (Fruit f : level.fruits()) {
                try {
                    if (f.isCollected()) continue;

                    Float fx = level.xOf(f.position().liana());
                    Integer logicalH = Integer.parseInt(f.position().height().value());
                    Float fy = heightToPixels(logicalH);

                    Rect fr = fruitRect(fx, fy);
                    Boolean overlap = rectOverlap(pr, fr);

                    if (overlap) {
                        System.out.println("[COLLISION] FRUIT COLLECTED! Points: " + f.points().value());
                        f.setCollected(Boolean.TRUE);
                        Integer pts = Integer.parseInt(f.points().value());
                        phys.addScore(pts);
                        System.out.println("[SCORE] New score: " + phys.score);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR FRUIT] " + e.getMessage());
                }
            }
        }
    }

    /**
     * Intenta que el jugador se aferre a la liana mas cercana dentro del rango.
     *
     * @param phys estado fisico del jugador
     */
    private void tryGrabLiana(PlayerPhysics phys) {
        var lianas = level.lianas();
        for (Integer i = Integer.valueOf(0); i < lianas.size(); i = i + 1) {
            if (lianas.get(i).canGrab(phys.x, phys.y, Float.valueOf(30.0f))) {
                phys.onLiana   = Boolean.TRUE;
                phys.lianaIndex = i;
                phys.x = lianas.get(i).x;
                phys.vx = Float.valueOf(0);
                phys.vy = Float.valueOf(0);
                break;
            }
        }
    }

    /**
     * Restablece el estado del jugador tras morir, reduciendo vidas y puntaje.
     *
     * @param phys estado fisico a reiniciar
     */
    private void respawn(PlayerPhysics phys) {
        System.out.println("════════════════════════════════════════");
        System.out.println("[RESPAWN DEBUG] Player died!");
        System.out.println("  Position: x=" + phys.x + ", y=" + phys.y);
        System.out.println("  Velocity: vx=" + phys.vx + ", vy=" + phys.vy);
        System.out.println("  onLiana: " + phys.onLiana + ", onGround: " + phys.onGround);
        System.out.println("  Score before reset: " + phys.score);
        System.out.println("════════════════════════════════════════");
        
        phys.x = Float.valueOf(150.0f);
        phys.y = Float.valueOf(490.0f);
        phys.vy = Float.valueOf(0);
        phys.onGround = Boolean.TRUE;
        phys.onLiana  = Boolean.FALSE;
        phys.lianaIndex = Integer.valueOf(-1);
        
        // Perder una vida
        phys.loseLife();
        
        // Resetear score al morir
        phys.resetScore();
        System.out.println("[RESPAWN] Score reset to 0");
        System.out.println("[RESPAWN] Lives remaining: " + phys.lives);
    }

    /**
     * Convierte una altura logica (0-12) a coordenadas en pixeles.
     *
     * @param logicalHeight altura logica global
     * @return posicion vertical equivalente en pixeles
     */
    private static Float heightToPixels(Integer logicalHeight) {
        // Mapea altura lógica (0-12) a píxeles del rango recortado (520-120)
        // logicalHeight=0 -> y=520 (abajo), logicalHeight=12 -> y=120 (arriba)
        Float min_y = Float.valueOf(120.0f);  // Top de la liana recortada
        Float max_y = Float.valueOf(520.0f);  // Bottom de la liana recortada
        Float range = max_y - min_y;  // 400 píxeles
        return max_y - (logicalHeight / 12.0f) * range;
    }

    /**
     * Rectangulo axis-aligned usado para deteccion de colisiones.
     */
    static final class Rect {
        final Float x, y, w, h;
        Rect(Float x, Float y, Float w, Float h){ this.x=x; this.y=y; this.w=w; this.h=h; }
    }

    /**
     * Determina si dos rectangulos se solapan.
     *
     * @param a primer rectangulo
     * @param b segundo rectangulo
     * @return {@code true} si existe interseccion
     */
    static Boolean rectOverlap(Rect a, Rect b){
        return a.x < b.x + b.w && a.x + a.w > b.x &&
               a.y < b.y + b.h && a.y + a.h > b.y;
    }

    /**
     * Construye el rectangulo de colision del jugador centrado en (x,y).
     *
     * @param x coordenada horizontal del centro
     * @param y coordenada vertical del centro
     * @return rectangulo axis-aligned del jugador
     */
    private static Rect playerRect(Float x, Float y){
        return new Rect(x - PLAYER_WIDTH*0.5f, y - PLAYER_HEIGHT*0.5f,
                        PLAYER_WIDTH, PLAYER_HEIGHT);
    }

    /**
     * Hitbox generica para cocodrilos, ajustando el sprite rojo o azul segun su
     * orientacion.
     *
     * @param x coordenada horizontal del centro
     * @param y coordenada vertical del centro
     * @param isRed indica si se usa la configuracion del cocodrilo rojo
     * @return rectangulo de colision principal
     */
    private static Rect crocRect(Float x, Float y, Boolean isRed) {
        // Hitbox para cocodrilo rojo (siempre vertical) - escala 2.0x
        Float w = Float.valueOf(52f);
        Float h = Float.valueOf(44f);
        return new Rect(x - w/2, y - h/2, w, h);
    }
    
    /**
     * Hitbox para cocodrilos azules mientras caminan en plataforma.
     */
    private static Rect crocBlueWalkingRect(Float x, Float y) {
        // Hitbox para azul caminando (rotado 90°, horizontal) - escala 2.0x
        // Cuando está horizontal: más ancho, menos alto
        Float w = Float.valueOf(44f);  // Ancho horizontal
        Float h = Float.valueOf(30f);  // Altura reducida para facilitar salto
        return new Rect(x - w/2, y - h/2, w, h);
    }
    
    /**
     * Hitbox para cocodrilos azules mientras descienden por una liana.
     */
    private static Rect crocBlueDescendingRect(Float x, Float y) {
        // Hitbox para azul bajando (vertical) - escala 2.0x
        // Cuando está vertical: menos ancho, más alto
        Float w = Float.valueOf(36f);  // Ancho vertical
        Float h = Float.valueOf(44f);  // Altura vertical
        return new Rect(x - w/2, y - h/2, w, h);
    }

    /**
     * Hitbox para frutas coleccionables.
     */
    private static Rect fruitRect(Float x, Float y) {
        // Hitbox ajustada para sprites escalados 1.8x
        Float w = Float.valueOf(25f), h = Float.valueOf(25f);
        return new Rect(x - w/2, y - h/2, w, h);
    }
}