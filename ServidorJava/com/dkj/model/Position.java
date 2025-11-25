package com.dkj.model;


import java.util.Objects;

/**
 * Posicion en el espacio del juego (Value Object).
 * 
 * Representa una ubicacion especifica en el mapa de Donkey Kong Jr mediante
 * dos coordenadas: el identificador de la liana y la altura vertical.
 * Este sistema de coordenadas discreto facilita la logica de juego y colisiones.
 * 
 * Componentes:
 * - liana: Identificador de la liana (eje horizontal discreto)
 * - height: Altura en la liana (eje vertical discreto)
 * 
 * Patrones aplicados: Value Object (DDD)
 */
public final class Position {
    private final LianaId liana;
    private final Height  height;
    
    /**
     * Crea una nueva posicion con la liana y altura especificadas.
     * 
     * @param liana Identificador de la liana. No puede ser null.
     * @param height Altura en la liana. No puede ser null.
     * @throws NullPointerException si liana o height son null
     */
    public Position(LianaId liana, Height height){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
    }
    
    /**
     * Obtiene el identificador de la liana.
     * 
     * @return LianaId representando la coordenada horizontal
     */
    public LianaId liana(){ return liana; }
    
    /**
     * Obtiene la altura en la liana.
     * 
     * @return Height representando la coordenada vertical
     */
    public Height  height(){ return height; }
    
    @Override public String toString(){ return "l="+liana+",h="+height; }
}
