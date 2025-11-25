package com.dkj.model;

/**
 * Liana vertical en el nivel de juego.
 * 
 * Representa una estructura vertical por la que el jugador puede trepar.
 * Las lianas tienen una posicion horizontal fija y limites verticales superior e inferior.
 * Son inmutables una vez creadas.
 * 
 * Sistema de coordenadas:
 * - x: Posicion horizontal fija de la liana en pixeles
 * - topY: Limite superior (Y menor) donde comienza la liana
 * - bottomY: Limite inferior (Y mayor) donde termina la liana
 * 
 * Funcionalidad:
 * - Deteccion de proximidad para agarrar la liana
 * - Limites verticales para trepar
 */
public final class Liana {
    public final Float x, topY, bottomY;
    
    /**
     * Crea una nueva liana con la posicion y limites especificados.
     * 
     * @param x Coordenada X fija de la liana
     * @param topY Coordenada Y del limite superior
     * @param bottomY Coordenada Y del limite inferior
     */
    public Liana(Float x, Float topY, Float bottomY) {
        this.x = x; this.topY = topY; this.bottomY = bottomY;
    }
    
    /**
     * Determina si el jugador puede agarrarse a esta liana.
     * 
     * Verifica si la posicion del jugador esta dentro del rango horizontal
     * y dentro de los limites verticales de la liana.
     * 
     * @param px Posicion X del jugador
     * @param py Posicion Y del jugador
     * @param range Rango horizontal maximo para agarrar la liana
     * @return true si el jugador puede agarrarse a la liana, false en caso contrario
     */
    public Boolean canGrab(Float px, Float py, Float range) {
        return Math.abs(px - x) < range && py > topY && py < bottomY;
    }
}