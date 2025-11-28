package com.dkj.game;

import com.dkj.entities.CrocodileBlue;
import com.dkj.entities.CrocodileRed;
import com.dkj.entities.DefaultEntityFactory;
import com.dkj.entities.Fruit;
import com.dkj.entities.Player;
import com.dkj.events.DeathEvent;
import com.dkj.events.GameEventBus;
import com.dkj.events.LevelEvent;
import com.dkj.events.ScoreEvent;
import com.dkj.events.StateEvent;
import com.dkj.model.Direction;
import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.PlayerId;
import com.dkj.model.PlayerPhysics;
import com.dkj.model.Points;
import com.dkj.model.Position;
import com.dkj.model.Speed;

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

    private Speed speed = new Speed("0.03");
    private Float baseSpeed = Float.valueOf(0.03f);  // velocidad base
    private Integer difficultyMultiplier = Integer.valueOf(1);  // multiplicador de dificultad

    private Integer ticksSinceLastBlueSpawn = Integer.valueOf(0);
    private static final Integer BLUE_SPAWN_INTERVAL = Integer.valueOf(360); // aprox cada 18 segundos
    
    private String lastError = ""; // Para guardar mensajes de error detallados

    private final Level level;
    private final PhysicsEngine physics;

    /**
     * Crea un nuevo juego inicializando el nivel, la fisica y las entidades
     * iniciales.
     *
     * @param bus bus de eventos al que se emitiran cambios de estado
     * @param factory fabrica utilizada para instanciar entidades del juego
     */
    public Game(final GameEventBus bus, final DefaultEntityFactory factory){
        this.bus = Objects.requireNonNull(bus);
        this.factory = Objects.requireNonNull(factory);

        this.level   = new Level();
        this.physics = new PhysicsEngine(level);

        // Spawneo inicial solo de rojos y frutas
        spawnInitialEntities();
        
        emitState();
    }

    /**
     * Genera las entidades iniciales (cocodrilo rojo y fruta) y registra
     * mensajes de diagnostico.
     */
    private void spawnInitialEntities() {
        // Spawn 1 cocodrilo rojo en liana 3, altura 6
        safeSpawnRed(new LianaId("3"), new Height("6"));
        
        // Spawn 1 fruta en liana 2, altura 8, con 100 puntos
        safeSpawnFruit(new LianaId("2"), new Height("8"), new Points("100"));
        
        System.out.println("[SPAWN] Initial entities spawned with speed: " + speed.value());
        System.out.println("[SPAWN] Difficulty level: " + difficultyMultiplier);
    }

    // ===== API ADMIN =====
    /**
     * Intenta crear un cocodrilo rojo controlado por el administrador.
     *
     * @param l identificador de la liana donde aparecera
     * @param h altura logica (0-12) solicitada
     * @return "OK" en caso de exito o un mensaje de error descriptivo
     */
    public String spawnCrocodileRed(final LianaId l, final Height h){
        lastError = ""; // Limpiar error anterior
        Boolean ok = safeSpawnRed(l, h);
        if (ok) {
            emitState();
            return "OK";
        } else {
            System.err.println("[GAME] Failed to spawn red crocodile: " + lastError);
            return "ERR " + lastError;
        }
    }

    /**
     * Spawner automatico de cocodrilos azules que entran caminando desde la
     * plataforma superior.
     *
     * @param platformHeight altura inicial sobre la plataforma
     */
    public void spawnCrocodileBlue(final Height platformHeight){
        // Crea un azul que empieza caminando en la plataforma
        var blue = factory.newBlue(platformHeight, speed);
        blues.add(blue);
        level.addCrocBlue(blue);
        emitState();
    }

    /**
     * Spawnea un cocodrilo azul descendiendo directamente por la liana
     * especificada.
     *
     * @param liana identificador de liana valido
     * @return "OK" si se crea correctamente o mensaje de error si la liana no existe
     */
    public String spawnCrocodileBlueOnLiana(final LianaId liana){
        if (!level.hasLiana(liana)) {
            System.err.println("[GAME] Cannot spawn blue: Liana " + liana.value() + " does not exist");
            return "ERR liana no existe";
        }
        // Calcula la altura inicial basándose en el topY de la liana específica
        Height initialHeight = level.getInitialHeightForLiana(liana);
        var blue = factory.newBlueOnLiana(liana, initialHeight, speed);
        blues.add(blue);
        level.addCrocBlue(blue);
        System.out.println("[GAME] Spawned blue on liana " + liana.value() + " at height " + initialHeight.value());
        emitState();
        return "OK";
    }

    /**
     * Crea una fruta en la liana y altura indicadas.
     *
     * @param l liana de destino
     * @param h altura logica (0-12)
     * @param p puntaje asociado a la fruta
     * @return "OK" al exito o mensaje de error informando la causa
     */
    public String spawnFruit(final LianaId l, final Height h, final Points p){
        lastError = ""; // Limpiar error anterior
        Boolean ok = safeSpawnFruit(l, h, p);
        if (ok) {
            emitState();
            return "OK";
        } else {
            System.err.println("[GAME] Failed to spawn fruit: " + lastError);
            return "ERR " + lastError;
        }
    }

    /**
     * Elimina una fruta que coincida con la posicion indicada.
     *
     * @param l liana donde se ubica la fruta
     * @param h altura logica en la liana
     */
    public void deleteFruit(final LianaId l, final Height h){
        fruits.removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        level.fruits().removeIf(f -> f.position().liana().equals(l) && f.position().height().equals(h));
        emitState();
    }

    // ===== PLAYERS =====
    /**
     * Registra un nuevo jugador en el motor de fisica y en la coleccion local.
     *
     * @param id identificador unico del jugador
     */
    public void addPlayer(final PlayerId id){
        physics.addPlayer(id);
        players.put(id, new Player(id, new Position(new LianaId("1"), new Height("0"))));
        emitState();
    }

    /**
     * Remueve al jugador de la partida y actualiza el estado compartido.
     *
     * @param id identificador del jugador a eliminar
     */
    public void removePlayer(final PlayerId id){
        physics.removePlayer(id);
        players.remove(id);
        emitState();
    }

    /**
     * Encola un movimiento que sera procesado en el siguiente paso de la
     * simulacion.
     *
     * @param id jugador que solicita el movimiento
     * @param dir direccion solicitada
     */
    public void enqueueMove(final PlayerId id, final Direction dir){
        physics.enqueueMove(id, dir);
    }

    /**
     * Avanza la simulacion un tick, actualizando fisica, entidades y spawners.
     */
    public void step(){
        Float dt = Float.valueOf(0.05f);

        physics.update(dt);
        
        // Detectar si algún jugador murió y resetear entidades
        for (var entry : physics.getPlayerPhysics().entrySet()) {
            var playerId = entry.getKey();
            var phys = entry.getValue();
            if (phys.respawned) {
                handleDeath(playerId);
                phys.clearRespawned(); // Limpiar flag después de manejar
                return; // Salir después de resetear para evitar problemas
            }
        }

        // Actualizar cocodrilos rojos
        for (var r : reds) {
            r.step();
        }

        // Actualizar cocodrilos azules
        List<CrocodileBlue> bluesToRemove = new ArrayList<>();
        for (var b : blues) {
            CrocodileBlue updated = b.step(level);
            if (updated == null) {
                bluesToRemove.add(b);
            }
        }
        blues.removeAll(bluesToRemove);
        level.crocodileBlues().removeAll(bluesToRemove);

        // SPAWNER de azules (automático cada 18 segundos)
        ticksSinceLastBlueSpawn = ticksSinceLastBlueSpawn + 1;
        if (ticksSinceLastBlueSpawn >= BLUE_SPAWN_INTERVAL) {
            System.out.println("[GAME] Spawning new blue crocodile (automatic) with speed: " + speed.value());
            spawnCrocodileBlue(new Height("12"));
            ticksSinceLastBlueSpawn = Integer.valueOf(0);
        }

        // No limpiar frutas aquí - se regeneran en reset

        emitState();
    }

    // ===== ADMIN CONSOLE =====
    /**
     * Interpreta comandos administrativos legacy en formato clave-valor.
     *
     * @param line linea original recibida desde la consola
     * @return respuesta textual indicando exito o error
     */
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
                        
                        LianaId liana = new LianaId(lStr);
                        Height height = new Height(hStr);
                        
                        if (!level.hasLiana(liana)) return "ERR liana no existe";
                        if (!level.isHeightValidFormat(height)) return "ERR altura debe ser 0-12";
                        
                        Boolean ok = safeSpawnRed(liana, height);
                        emitState();
                        return ok ? "OK red" : "ERR spawn failed";
                    }
                    case "blue" -> {
                        String lStr = kv.get("l");
                        if (lStr == null) return "ERR falta l";
                        LianaId liana = new LianaId(lStr);
                        if (!level.hasLiana(liana)) return "ERR liana no existe";
                        spawnCrocodileBlueOnLiana(liana);
                        return "OK blue";
                    }
                    case "fruit" -> {
                        String lStr = kv.get("l");
                        String hStr = kv.getOrDefault("h", "8");
                        String ptsStr = kv.getOrDefault("pts", "100");
                        if (lStr == null) return "ERR falta l";
                        
                        LianaId liana = new LianaId(lStr);
                        Height height = new Height(hStr);
                        
                        if (!level.hasLiana(liana)) return "ERR liana no existe";
                        if (!level.isHeightValidFormat(height)) return "ERR altura debe ser 0-12";
                        
                        Boolean ok = safeSpawnFruit(liana, height, new Points(ptsStr));
                        emitState();
                        return ok ? "OK fruit" : "ERR spawn failed";
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

    // ===== VICTORIA =====
    /**
     * Gestiona la victoria de un jugador ajustando dificultad y reiniciando el
     * estado del juego.
     *
     * @param pid identificador del jugador victorioso
     */
    public void handleVictory(PlayerId pid) {
        Player p = players.get(pid);
        if (p == null) return;
        
        PlayerPhysics phys = physics.getPhysicsFor(pid);
        if (phys == null) return;
        
        System.out.println("════════════════════════════════════════");
        System.out.println("[VICTORY] Player " + pid.value() + " won!");
        System.out.println("  Lives before: " + phys.lives);
        System.out.println("  Difficulty before: " + phys.difficultyLevel);
        
        // Dar una vida adicional
        phys.addLife();
        
        // Aumentar dificultad
        phys.increaseDifficulty();
        difficultyMultiplier = phys.difficultyLevel;
        
        // Calcular nueva velocidad (aumenta 20% por cada nivel)
        Float newSpeedValue = baseSpeed * (1.0f + (difficultyMultiplier - 1) * 0.2f);
        speed = new Speed(String.format("%.4f", newSpeedValue));
        
        System.out.println("  Lives after: " + phys.lives);
        System.out.println("  Difficulty after: " + phys.difficultyLevel);
        System.out.println("  New speed: " + speed.value());
        System.out.println("════════════════════════════════════════");
        
        // Resetear el juego
        resetGame(phys);
    }
    
    /**
     * Restablece el juego tras una victoria conservando progreso y aumentando
     * la dificultad.
     *
     * @param phys estado de fisica del jugador principal
     */
    private void resetGame(PlayerPhysics phys) {
        System.out.println("[RESET] Resetting game with increased difficulty...");
        
        // Limpiar cocodrilos
        reds.clear();
        blues.clear();
        level.crocodileReds().clear();
        level.crocodileBlues().clear();
        
        // Resetear frutas existentes en lugar de eliminarlas
        for (var fruit : fruits) {
            fruit.setCollected(Boolean.FALSE);
        }
        
        // Resetear posición del jugador
        phys.x = Float.valueOf(150.0f);
        phys.y = Float.valueOf(490.0f);
        phys.vx = Float.valueOf(0);
        phys.vy = Float.valueOf(0);
        phys.onGround = Boolean.TRUE;
        phys.onLiana = Boolean.FALSE;
        phys.lianaIndex = Integer.valueOf(-1);
        
        // Resetear score (empieza desde 0 en el nuevo nivel)
        phys.resetScore();
        
        // Resetear timer de spawn de azules
        ticksSinceLastBlueSpawn = Integer.valueOf(0);
        
        // Spawn inicial de cocodrilos rojos (las frutas ya existen)
        safeSpawnRed(new LianaId("3"), new Height("6"));
        
        System.out.println("[RESET] Game reset complete. New difficulty level: " + difficultyMultiplier);
        System.out.println("[RESET] NEW SPEED: " + speed.value() + " (Base: " + baseSpeed + ", Multiplier: 1 + " + (difficultyMultiplier - 1) + " * 0.2)");
        System.out.println("[RESET] Player position: x=" + phys.x + ", y=" + phys.y);
        System.out.println("[RESET] Player state: onGround=" + phys.onGround + ", onLiana=" + phys.onLiana);
        System.out.println("[RESET] Lives: " + phys.lives);
        
        emitState();
    }
    
    /**
     * Maneja la muerte de un jugador notificando a los observadores y
     * reiniciando entidades.
     *
     * @param playerId jugador que ha perdido una vida
     */
    public void handleDeath(PlayerId playerId) {
        var phys = physics.getPhysicsFor(playerId);
        if (phys == null) return;
        
        System.out.println("[DEATH] Player " + playerId.value() + " died! Resetting entities...");
        
        // Emitir evento de muerte
        bus.emit(new DeathEvent(playerId));
        
        // Resetear todas las entidades pero mantener vidas y dificultad
        resetEntitiesOnly();
    }
    
    /**
     * Reestablece las entidades sin modificar el estado de dificultad ni las
     * vidas de los jugadores.
     */
    private void resetEntitiesOnly() {
        // Limpiar cocodrilos
        reds.clear();
        blues.clear();
        level.crocodileReds().clear();
        level.crocodileBlues().clear();
        
        // Resetear frutas existentes en lugar de eliminarlas
        for (var fruit : fruits) {
            fruit.setCollected(Boolean.FALSE);
        }
        
        // Resetear timer de spawn de azules
        ticksSinceLastBlueSpawn = Integer.valueOf(0);
        
        // Spawn inicial de cocodrilos rojos (las frutas ya existen)
        safeSpawnRed(new LianaId("3"), new Height("6"));
        
        System.out.println("[RESET] Entities reset complete. Fruits regenerated.");
        
        emitState();
    }
    
    // ===== SERIALIZACIÓN =====
    /**
     * Emite el estado actual a todos los observadores suscritos.
     */
    private void emitState(){
        bus.emit(new StateEvent(snapshot()));
    }

    /**
     * Construye una representacion textual del estado del juego usada por el
     * protocolo de red.
     *
     * @return cadena que resume jugadores, cocodrilos y frutas activos
     */
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
                    .append(",score=").append(phys.score)
                    .append(",lives=").append(phys.lives);
        }

        var redsTxt = new StringBuilder();
        for (var r : reds) {
            var p = r.position();
            if (redsTxt.length() > Integer.valueOf(0)) redsTxt.append("|");
            redsTxt.append("l=").append(p.liana().value())
                .append(",h=").append(p.height().value())
                .append(",goingUp=").append(r.isGoingUp() ? "1" : "0");
        }

        var bluesTxt = new StringBuilder();
        for (var b : blues) {
            var p = b.position();
            if (bluesTxt.length() > Integer.valueOf(0)) bluesTxt.append("|");
            
            if (b.getState() == CrocodileBlue.CrocodileBlueState.WALKING_ON_PLATFORM) {
                bluesTxt.append("state=walking")
                    .append(",x=").append(String.format("%.1f", b.getPlatformX()))
                    .append(",h=").append(p.height().value());
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
    /**
     * Intenta crear un cocodrilo rojo validando previamente liana y altura.
     *
     * @param l liana destino del cocodrilo
     * @param h altura logica solicitada
     * @return {@code true} si se crea correctamente, {@code false} en caso de error
     */
    private Boolean safeSpawnRed(LianaId l, Height h) {
        if (!level.hasLiana(l)) {
            System.err.println("[GAME] Cannot spawn red: Liana " + l.value() + " does not exist");
            lastError = "liana no existe";
            return Boolean.FALSE;
        }
        
        // Validar formato de altura (0-12)
        if (!level.isHeightValidFormat(h)) {
            System.err.println("[GAME] Cannot spawn red: Height must be 0-12, got " + h.value());
            lastError = "altura debe ser 0-12";
            return Boolean.FALSE;
        }
        
        // Mapear altura lógica (0-12) al espacio real de esta liana
        Height mappedHeight = level.mapHeightToLiana(l, h);
        
        // Obtener límites en el espacio real para el cocodrilo
        Float minH = level.getMinHeightForLiana(l);
        Float maxH = level.getMaxHeightForLiana(l);
        var red = factory.newRed(l, mappedHeight, speed, minH, maxH);
        reds.add(red);
        level.addCrocRed(red);
        System.out.println("[GAME] Spawned red on liana " + l.value() + " at logical height " + h.value() + "/12");
        return Boolean.TRUE;
    }

    /**
     * Intenta crear una fruta validando liana, altura y generando logs.
     *
     * @param l liana donde aparecera la fruta
     * @param h altura logica solicitada
     * @param p puntaje asociado
     * @return {@code true} al exito, {@code false} si alguna validacion falla
     */
    private Boolean safeSpawnFruit(LianaId l, Height h, Points p) {
        if (!level.hasLiana(l)) {
            System.err.println("[GAME] Cannot spawn fruit: Liana " + l.value() + " does not exist");
            lastError = "liana no existe";
            return Boolean.FALSE;
        }
        
        // Validar formato de altura (0-12)
        if (!level.isHeightValidFormat(h)) {
            System.err.println("[GAME] Cannot spawn fruit: Height must be 0-12, got " + h.value());
            lastError = "altura debe ser 0-12";
            return Boolean.FALSE;
        }
        
        // Mapear altura lógica (0-12) al espacio real de esta liana
        Height mappedHeight = level.mapHeightToLiana(l, h);
        
        var fruit = factory.newFruit(l, mappedHeight, p);
        fruits.add(fruit);
        level.addFruit(fruit);
        System.out.println("[GAME] Spawned fruit on liana " + l.value() + " at logical height " + h.value() + "/12");
        return Boolean.TRUE;
    }
}