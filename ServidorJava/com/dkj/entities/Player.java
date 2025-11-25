package com.dkj.entities;

import com.dkj.model.PlayerId;
import com.dkj.model.Position;

import java.util.Objects;

/**
 * Representacion del jugador en el dominio del juego.
 * 
 * Encapsula la identidad y posicion de un jugador dentro del espacio del juego.
 * A diferencia de PlayerPhysics (que maneja estado fisico detallado con pixeles),
 * esta clase mantiene el estado a nivel de dominio con posiciones discretas.
 * 
 * Responsabilidades:
 * - Mantener la identidad unica del jugador
 * - Almacenar la posicion actual en coordenadas del dominio (liana + altura)
 * - Proveer operaciones de movimiento semantico
 * 
 * Nota: La fisica detallada (velocidad, colisiones, etc.) se maneja en PlayerPhysics.
 */
public final class Player {
    private final PlayerId id;
    private Position position;

    /**
     * Crea un nuevo jugador con el identificador y posicion inicial especificados.
     * 
     * @param id Identificador unico del jugador. No puede ser null.
     * @param start Posicion inicial del jugador. No puede ser null.
     * @throws NullPointerException si id o start son null
     */
    public Player(PlayerId id, Position start){
        this.id = Objects.requireNonNull(id);
        this.position = Objects.requireNonNull(start);
    }

    /**
     * Obtiene el identificador del jugador.
     * 
     * @return PlayerId del jugador
     */
    public PlayerId id(){ return id; }
    
    /**
     * Obtiene la posicion actual del jugador.
     * 
     * @return Position actual del jugador en el dominio
     */
    public Position position(){ return position; }

    /**
     * Mueve el jugador a una nueva posicion (operacion semantica de dominio).
     * 
     * @param p Nueva posicion del jugador. No puede ser null.
     * @throws NullPointerException si p es null
     */
    public void moveTo(Position p){
        this.position = Objects.requireNonNull(p);
    }

    /**
     * Establece la posicion del jugador directamente.
     * 
     * Metodo explicito utilizado por Game.processMoves para actualizar
     * la posicion tras procesar la fisica.
     * 
     * @param p Nueva posicion del jugador. No puede ser null.
     * @throws NullPointerException si p es null
     */
    public void setPosition(Position p){
        this.position = Objects.requireNonNull(p);
    }
}
