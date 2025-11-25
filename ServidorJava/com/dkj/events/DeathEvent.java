package com.dkj.events;

import com.dkj.model.PlayerId;

/**
 * Evento de muerte de jugador.
 * 
 * Notifica que un jugador ha muerto por colision con un enemigo.
 * Permite a los clientes mostrar efectos visuales (flash de respawn)
 * y actualizar el estado del jugador.
 * 
 * Uso: Se emite cuando el jugador colisiona con un cocodrilo (rojo o azul).
 */
public final class DeathEvent implements GameEvent {
    private final PlayerId player;
    
    /**
     * Crea un nuevo evento de muerte.
     * 
     * @param p Identificador del jugador que murio
     */
    public DeathEvent(PlayerId p){ 
        this.player=p; 
    }
    
    /**
     * Obtiene el jugador que murio.
     * 
     * @return PlayerId del jugador fallecido
     */
    public PlayerId player(){ return player; }
}
