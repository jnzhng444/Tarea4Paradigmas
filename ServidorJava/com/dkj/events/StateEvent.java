package com.dkj.events;

/**
 * Evento de actualizacion de estado del juego.
 * 
 * Representa un snapshot completo del estado actual del juego, incluyendo
 * posiciones de jugadores, enemigos y frutas. Se emite periodicamente
 * (cada tick) para mantener a los clientes sincronizados.
 * 
 * Formato del payload:
 * Linea de texto protocolo STATE con toda la informacion serializada del mundo,
 * lista para ser enviada directamente a los clientes.
 * 
 * Ejemplo: "STATE pl1:150.0,490.0,0.0,0.0,1,0,-1,100,0,1,0 red:3,6,0.03,6.0,12.0,1 ..."
 */
public final class StateEvent implements GameEvent {
    private final String payload; // línea STATE lista para enviar
    
    /**
     * Crea un nuevo evento de estado con el payload especificado.
     * 
     * @param payload Linea STATE completa serializada lista para enviar a clientes
     */
    public StateEvent(String payload){ this.payload = payload; }
    
    /**
     * Obtiene el payload del evento.
     * 
     * @return String con la linea STATE completa para broadcast
     */
    public String payload(){ return payload; }
}
