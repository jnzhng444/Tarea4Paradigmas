package com.dkj.events;



/**
 * Interfaz para observers de eventos del juego.
 * 
 * Define el contrato que deben implementar los componentes que desean
 * recibir notificaciones de eventos del juego. Parte del patron Observer.
 * 
 * Uso tipico: GameRoom implementa esta interfaz para recibir eventos del Game
 * y luego broadcast los mensajes a los clientes conectados.
 * 
 * Patron aplicado: Observer (GoF)
 */
public interface GameEventListener {
    /**
     * Callback invocado cuando ocurre un evento en el juego.
     * 
     * Este metodo es llamado por GameEventBus cada vez que se emite un evento.
     * La implementacion debe ser rapida para no bloquear el game loop.
     * 
     * @param e Evento que acaba de ocurrir en el juego
     */
    void onEvent(GameEvent e);
}
