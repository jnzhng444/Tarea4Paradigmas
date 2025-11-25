package com.dkj.events;



import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bus de eventos del juego (Observer pattern).
 * 
 * Implementa el patron Observer para desacoplar la logica de juego de la
 * comunicacion con clientes. Permite que multiples componentes (GameRoom)
 * se suscriban para recibir notificaciones de eventos del juego sin que
 * la logica central necesite conocer a los observadores.
 * 
 * Caracteristicas:
 * - Thread-safe: Usa CopyOnWriteArrayList para operaciones concurrentes
 * - Broadcast: Emite eventos a todos los suscriptores registrados
 * - Desacoplamiento: Game no conoce directamente a los clientes
 * 
 * Tipos de eventos soportados:
 * - StateEvent: Actualizacion completa del estado del juego
 * - ScoreEvent: Jugador gano puntos
 * - DeathEvent: Jugador murio
 * - LevelEvent: Cambio de nivel o velocidad
 * 
 * Patron aplicado: Observer (GoF)
 */
public final class GameEventBus {
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * Suscribe un listener para recibir eventos del juego.
     * 
     * @param l Listener a registrar. Se invocara su metodo onEvent() para cada evento emitido.
     */
    public void subscribe(GameEventListener l){ listeners.add(l); }
    
    /**
     * Desuscribe un listener del bus de eventos.
     * 
     * @param l Listener a eliminar. Dejara de recibir notificaciones.
     */
    public void unsubscribe(GameEventListener l){ listeners.remove(l); }
    
    /**
     * Emite un evento a todos los listeners suscritos.
     * 
     * Notifica secuencialmente a cada listener registrado. Los listeners
     * se ejecutan de forma sincrona en el hilo emisor.
     * 
     * @param e Evento a emitir a todos los suscriptores
     */
    public void emit(GameEvent e){ for (var l : listeners) l.onEvent(e); }
}
