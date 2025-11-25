package com.dkj.model;



/**
 * Enumeracion de roles de cliente en el sistema.
 * 
 * Define los dos tipos de participantes que pueden conectarse al servidor:
 * - PLAYER: Jugador activo que controla un personaje en el juego
 * - SPECTATOR: Observador que puede ver la partida de un jugador pero no interactuar
 * 
 * El rol determina los permisos y comandos disponibles para cada cliente conectado.
 */
public enum Role { 
    /** Jugador activo que puede enviar comandos de movimiento */
    PLAYER, 
    
    /** Espectador que solo recibe actualizaciones de estado del juego */
    SPECTATOR 
}
