package com.dkj.game;

import com.dkj.entities.DefaultEntityFactory;
import com.dkj.events.DeathEvent;
import com.dkj.events.GameEventBus;
import com.dkj.events.LevelEvent;
import com.dkj.events.ScoreEvent;
import com.dkj.events.StateEvent;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Representa una sala de juego individual que mantiene un {@link Game}, su
 * loop de simulacion y las conexiones asociadas (jugador y espectadores).
 */
public final class GameRoom {

    private static final Integer MAX_SPECTATORS = Integer.valueOf(2);

    private final GameEventBus bus = new GameEventBus();
    private final Game game;
    private final GameLoop loop;

    // Separar player de spectators para controlar el límite
    private PrintWriter playerOut = null;
    private final CopyOnWriteArrayList<PrintWriter> spectatorOuts = new CopyOnWriteArrayList<>();

    /**
     * Crea una sala inicializando el juego, el loop y suscribiendo los handlers
     * de eventos para difusion a los clientes adjuntos.
     *
     * @param factory fabrica utilizada para instanciar entidades dentro del juego
     */
    public GameRoom(DefaultEntityFactory factory) {
        // el Game se construye con la fábrica explícita (Factory Method visible)
        this.game = new Game(bus, factory);
        this.loop = new GameLoop(game, "16"); 
        this.loop.start();

        // Suscripción a eventos del dominio para difundir a los adjuntos de esta sala
        bus.subscribe(e -> {
            if (e instanceof StateEvent s) {
                broadcast(s.payload());
            } else if (e instanceof LevelEvent l) {
                broadcast("LEVEL " + l.level() + " SPEED " + l.speed().value());
            } else if (e instanceof ScoreEvent sc) {
                broadcast("SCORE " + sc.player().value() + " " + sc.points().value());
            } else if (e instanceof DeathEvent d) {
                broadcast("DEAD " + d.player().value());
            }
        });
    }

    /**
     * Obtiene la instancia de juego asociada a esta sala.
     *
     * @return instancia de {@link Game} mantenida por la sala
     */
    public Game game() { return game; }

    /**
     * Adjunta el canal de salida del jugador principal si aun no hay uno.
     *
     * @param out writer conectado al cliente jugador
     */
    public void attach(PrintWriter out){
        if (out != null && playerOut == null) {
            playerOut = out;
        }
    }

    /**
     * Adjunta un espectador respetando el limite de la sala.
     *
     * @param out writer del cliente espectador
     * @return {@code true} si se agrego con exito, {@code false} si la sala esta llena o el writer es null
     */
    public Boolean attachSpectator(PrintWriter out){
        if (out == null) return Boolean.FALSE;
        if (spectatorOuts.size() >= MAX_SPECTATORS) {
            return Boolean.FALSE;
        }
        spectatorOuts.add(out);
        return Boolean.TRUE;
    }

    /**
     * Desacopla un writer, ya sea del jugador principal o de la lista de
     * espectadores.
     *
     * @param out canal de salida a eliminar de la sala
     */
    public void detach(PrintWriter out){
        if (out == null) return;
        if (out == playerOut) {
            playerOut = null;
        } else {
            spectatorOuts.remove(out);
        }
    }

    /**
     * Indica cuántos espectadores hay actualmente conectados a la sala.
     *
     * @return cantidad de espectadores activos
     */
    public Integer spectatorCount() {
        return spectatorOuts.size();
    }

    /**
     * Detiene el loop de juego asociado liberando su hilo de ejecucion.
     */
    public void stop() {
        try { loop.stop(); } catch (Exception ignored) {}
    }

    /**
     * Informa a todos los espectadores que el jugador principal se ha
     * desconectado para que los clientes actualicen la interfaz.
     */
    public void notifyPlayerDisconnected() {
        for (var w : spectatorOuts) {
            try { 
                w.println("PLAYER_DISCONNECTED"); 
                w.flush();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Difunde una linea de texto tanto al jugador como a los espectadores.
     *
     * @param line mensaje a enviar
     */
    private void broadcast(String line){
        // Enviar al jugador
        if (playerOut != null) {
            try { playerOut.println(line); } catch (Exception ignored) {}
        }
        // Enviar a espectadores
        for (var w : spectatorOuts) {
            try { w.println(line); } catch (Exception ignored) {}
        }
    }
}