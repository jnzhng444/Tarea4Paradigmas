package com.dkj.model;

/**
 * Plataforma horizontal en el nivel de juego.
 * 
 * Representa una superficie solida rectangular sobre la cual el jugador
 * puede caminar. Las plataformas son inmutables una vez creadas.
 * 
 * Sistema de coordenadas:
 * - x, y: Posicion de la esquina superior izquierda en pixeles
 * - w: Ancho de la plataforma en pixeles
 * - h: Alto de la plataforma en pixeles
 * 
 * Las plataformas se utilizan en el motor de fisica para detectar colisiones
 * y determinar cuando el jugador esta sobre el suelo.
 */
public final class Platform {
    public final Float x, y, w, h;
    
    /**
     * Crea una nueva plataforma con las dimensiones especificadas.
     * 
     * @param x Coordenada X de la esquina superior izquierda
     * @param y Coordenada Y de la esquina superior izquierda
     * @param w Ancho de la plataforma
     * @param h Alto de la plataforma
     */
    public Platform(Float x, Float y, Float w, Float h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }
}