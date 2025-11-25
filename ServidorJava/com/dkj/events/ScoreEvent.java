package com.dkj.events;

import com.dkj.model.PlayerId;
import com.dkj.model.Points;

/**
 * Evento de puntos ganados por un jugador.
 * 
 * Notifica que un jugador ha ganado puntos, tipicamente por recoger una fruta.
 * Permite a los clientes mostrar feedback visual (popups de puntos) y actualizar
 * el HUD de puntaje.
 * 
 * Uso: Se emite cuando el jugador colisiona con una fruta no recolectada.
 */
public final class ScoreEvent implements GameEvent {
    private final PlayerId player; 
    private final Points points;
    
    /**
     * Crea un nuevo evento de puntaje.
     * 
     * @param p Identificador del jugador que gano puntos
     * @param pts Cantidad de puntos ganados
     */
    public ScoreEvent(PlayerId p, Points pts){ 
        this.player=p; 
        this.points=pts; 
    }
    
    /**
     * Obtiene el jugador que gano los puntos.
     * 
     * @return PlayerId del jugador
     */
    public PlayerId player(){ return player; } 
    
    /**
     * Obtiene la cantidad de puntos ganados.
     * 
     * @return Points ganados en este evento
     */
    public Points points(){ return points; }
}
