package com.dkj.model;


/**
 * Enumeracion de direcciones de movimiento del jugador.
 * 
 * Define las cinco direcciones posibles que un jugador puede solicitar:
 * - UP: Subir por una liana
 * - DOWN: Bajar por una liana
 * - LEFT: Moverse hacia la izquierda
 * - RIGHT: Moverse hacia la derecha
 * - JUMP: Saltar
 * 
 * Incluye metodo de conversion desde String para parseo de comandos de red.
 */
public enum Direction {
    /** Movimiento ascendente por liana */
    UP, 
    
    /** Movimiento descendente por liana */
    DOWN, 
    
    /** Movimiento horizontal hacia la izquierda */
    LEFT, 
    
    /** Movimiento horizontal hacia la derecha */
    RIGHT, 
    
    /** Accion de salto */
    JUMP;

    /**
     * Convierte un String a la direccion correspondiente.
     * 
     * Parsea el texto ignorando mayusculas/minusculas y retorna la direccion
     * correspondiente. Si el texto no coincide con ninguna direccion, retorna null.
     * 
     * @param s String a convertir (case-insensitive)
     * @return Direction correspondiente, o null si el texto no es valido
     */
    public static Direction fromString(String s) {
        try {
            return Direction.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
