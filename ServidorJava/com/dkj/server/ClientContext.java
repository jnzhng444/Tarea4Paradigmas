package com.dkj.server;

import com.dkj.model.PlayerId;
import com.dkj.model.Role;

import java.io.PrintWriter;

/**
 * Contexto de sesion de un cliente conectado al servidor.
 * 
 * Encapsula toda la informacion de estado asociada a una conexion de cliente individual,
 * incluyendo su canal de comunicacion de salida, identificador de jugador y rol en el sistema.
 * Esta clase actua como un objeto de transferencia de datos (DTO) que se pasa entre
 * diferentes capas del servidor.
 * 
 * Componentes:
 * - PrintWriter: Canal de salida para enviar mensajes al cliente
 * - PlayerId: Identificador unico del jugador (null si aun no se ha autenticado)
 * - Role: Rol del cliente en el sistema (PLAYER o SPECTATOR)
 */
public final class ClientContext {
    private final PrintWriter out;
    private PlayerId playerId;
    private Role role;

    /**
     * Crea un nuevo contexto de cliente con el canal de salida especificado.
     * 
     * @param out Canal de salida (PrintWriter) para comunicacion con el cliente
     */
    public ClientContext(PrintWriter out){
        this.out = out;
    }

    /**
     * Obtiene el canal de salida del cliente.
     * 
     * @return PrintWriter para enviar mensajes al cliente
     */
    public PrintWriter out(){ return out; }

    /**
     * Obtiene el identificador del jugador asociado a este contexto.
     * 
     * @return PlayerId del jugador, o null si aun no se ha asignado
     */
    public PlayerId playerId(){ return playerId; }
    
    /**
     * Establece el identificador del jugador para este contexto.
     * 
     * @param id Identificador del jugador a asignar
     */
    public void playerId(PlayerId id){ this.playerId = id; }

    /**
     * Obtiene el rol del cliente en el sistema.
     * 
     * @return Role del cliente (PLAYER o SPECTATOR), o null si aun no se ha asignado
     */
    public Role role(){ return role; }
    
    /**
     * Establece el rol del cliente en el sistema.
     * 
     * @param r Rol a asignar al cliente
     */
    public void role(Role r){ this.role = r; }
}
