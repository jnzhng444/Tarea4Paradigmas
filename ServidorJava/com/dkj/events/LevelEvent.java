package com.dkj.events;

import com.dkj.model.Speed;

/**
 * Evento de cambio de nivel o velocidad del juego.
 * 
 * Notifica que el juego ha avanzado a un nuevo nivel, lo cual tipicamente
 * viene acompanado de un incremento en la velocidad de los enemigos para
 * aumentar la dificultad.
 * 
 * Uso: Se emite cuando un jugador completa un nivel exitosamente.
 */
public final class LevelEvent implements GameEvent {
    private final String level; 
    private final Speed speed;
    
    /**
     * Crea un nuevo evento de nivel.
     * 
     * @param level Numero del nuevo nivel como String
     * @param speed Nueva velocidad de enemigos para este nivel
     */
    public LevelEvent(String level, Speed speed){ 
        this.level=level; 
        this.speed=speed; 
    }
    
    /**
     * Obtiene el numero del nuevo nivel.
     * 
     * @return String representando el nivel actual
     */
    public String level(){ return level; } 
    
    /**
     * Obtiene la nueva velocidad de los enemigos.
     * 
     * @return Speed configurada para este nivel
     */
    public Speed speed(){ return speed; }
}
