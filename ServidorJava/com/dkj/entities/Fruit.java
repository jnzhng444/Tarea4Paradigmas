package com.dkj.entities;

import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.Points;
import com.dkj.model.Position;

import java.util.Objects;

/**
 * Fruta coleccionable en el juego (Entidad del dominio).
 * 
 * Representa una fruta que el jugador puede recoger para ganar puntos.
 * Cada fruta tiene una posicion fija (liana + altura) y un valor en puntos.
 * Las frutas pueden estar en estado "recolectada" o "disponible".
 * 
 * Comportamiento:
 * - Una vez creada, su posicion y valor no cambian (inmutables)
 * - Puede marcarse como recolectada cuando el jugador la obtiene
 * - Las frutas recolectadas permanecen en memoria pero no se dibujan
 * 
 * Implementa Entity para integrarse en el sistema de entidades del juego.
 */
public final class Fruit implements Entity {
    private final LianaId liana;
    private final Height  height;
    private final Points  points;
    private Boolean collected = Boolean.FALSE; // <- NUEVO

    /**
     * Crea una nueva fruta con la posicion y valor especificados.
     * 
     * @param liana Identificador de la liana donde aparece. No puede ser null.
     * @param height Altura en la liana. No puede ser null.
     * @param points Puntos que otorga al ser recolectada. No puede ser null.
     * @throws NullPointerException si liana, height o points son null
     */
    public Fruit(LianaId liana, Height height, Points points){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.points = Objects.requireNonNull(points);
    }

    /**
     * Obtiene el valor en puntos de esta fruta.
     * 
     * @return Points que otorga la fruta al jugador
     */
    public Points points(){ return points; }
    
    /**
     * Obtiene la posicion de la fruta en el espacio del juego.
     * 
     * @return Position compuesta por liana y altura
     */
    @Override public Position position(){ return new Position(liana, height); }

    /**
     * Verifica si la fruta ya fue recolectada.
     * 
     * @return true si la fruta fue recolectada, false si esta disponible
     */
    public Boolean isCollected(){ return collected; }
    
    /**
     * Establece el estado de recoleccion de la fruta.
     * 
     * @param v true para marcar como recolectada, false para disponible
     */
    public void setCollected(Boolean v){ this.collected = v; }
}