package com.dkj.game;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * Loop principal del juego que ejecuta la simulacion periodicamente.
 * 
 * Implementa un loop de juego con tick rate fijo que invoca el metodo step()
 * del Game a intervalos regulares. Utiliza ScheduledExecutorService para
 * garantizar ejecucion periodica precisa en un hilo dedicado.
 * 
 * Responsabilidades:
 * - Iniciar/detener el loop de simulacion
 * - Invocar Game.step() a tasa fija (tipicamente 16ms = 60fps)
 * - Gestionar el hilo de ejecucion del juego
 * 
 * Ciclo de vida:
 * 1. Construccion con Game y periodo en milisegundos
 * 2. start() inicia el loop periodico
 * 3. stop() detiene el loop y libera recursos
 */
public final class GameLoop {
    private final Game game;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final String tickMillis; // VO evitar primitivo visible (p.ej. "200")

    /**
     * Crea un nuevo loop de juego.
     * 
     * @param game Instancia del juego a simular. No puede ser null.
     * @param tickMillis Periodo entre ticks en milisegundos como String (ej: "16" para 60fps)
     * @throws NullPointerException si game o tickMillis son null
     */
    public GameLoop(Game game, String tickMillis){
        this.game = Objects.requireNonNull(game);
        this.tickMillis = Objects.requireNonNull(tickMillis);
    }

    /**
     * Inicia el loop de juego.
     * 
     * Comienza a ejecutar Game.step() periodicamente con el intervalo configurado.
     * El primer tick se ejecuta despues del primer periodo completo.
     */
    public void start(){
        Long period = Long.parseLong(tickMillis); // infraestructura, no dominio
        exec.scheduleAtFixedRate(game::step, period, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Detiene el loop de juego inmediatamente.
     * 
     * Interrumpe el hilo de ejecucion y libera recursos del executor.
     * Llamar a start() despues de stop() no reiniciara el loop.
     */
    public void stop(){ exec.shutdownNow(); }
}