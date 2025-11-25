package com.dkj.commands;

import com.dkj.model.Direction;
import com.dkj.model.PlayerId;

import java.util.Objects;

/**
 * Comando de movimiento de jugador (Command pattern).
 * 
 * Encapsula una solicitud de movimiento de un jugador en una direccion especifica.
 * Actua como un objeto de datos inmutable que transporta la intencion del jugador
 * desde la capa de red hasta el motor de juego.
 * 
 * Componentes:
 * - player: Identificador del jugador que solicita el movimiento
 * - dir: Direccion del movimiento solicitado (UP, DOWN, LEFT, RIGHT, JUMP)
 * 
 * Patron aplicado: Command (GoF) - Version simplificada como DTO
 */
public final class MoveCommand {
    private final PlayerId player;
    private final Direction dir;

    /**
     * Crea un nuevo comando de movimiento.
     * 
     * @param player Identificador del jugador. No puede ser null.
     * @param dir Direccion del movimiento. No puede ser null.
     * @throws NullPointerException si player o dir son null
     */
    public MoveCommand(PlayerId player, Direction dir) {
        this.player = Objects.requireNonNull(player);
        this.dir = Objects.requireNonNull(dir);
    }

    /**
     * Obtiene el jugador que solicita el movimiento.
     * 
     * @return PlayerId del jugador
     */
    public PlayerId player() { return player; }
    
    /**
     * Obtiene la direccion del movimiento solicitado.
     * 
     * @return Direction del movimiento
     */
    public Direction dir() { return dir; }
}
