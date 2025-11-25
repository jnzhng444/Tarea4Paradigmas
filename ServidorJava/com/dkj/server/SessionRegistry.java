package com.dkj.server;



import java.util.List;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro centralizado de sesiones de clientes conectados al servidor.
 * 
 * Esta clase mantiene un registro thread-safe de todos los contextos de cliente
 * actualmente conectados al servidor, tanto jugadores como espectadores.
 * Utiliza CopyOnWriteArrayList para garantizar seguridad en operaciones concurrentes
 * sin necesidad de sincronizacion explicita.
 * 
 * Responsabilidades:
 * - Mantener registro de todas las sesiones activas
 * - Agregar nuevos jugadores y espectadores
 * - Eliminar sesiones desconectadas
 * - Proveer snapshot inmutable para consultas administrativas
 */
public final class SessionRegistry {
    private final CopyOnWriteArrayList<ClientContext> all = new CopyOnWriteArrayList<>();

    /**
     * Registra un nuevo jugador en el sistema.
     * 
     * Agrega el contexto del cliente a la lista de sesiones activas si no existe previamente.
     * Esta operacion es thread-safe y no bloqueante.
     * 
     * @param ctx Contexto del cliente jugador a registrar. Si es null, no se realiza ninguna accion.
     */
    public void addPlayer(ClientContext ctx) {
        if (ctx != null) all.addIfAbsent(ctx);
    }

    /**
     * Registra un nuevo espectador en el sistema.
     * 
     * Agrega el contexto del cliente a la lista de sesiones activas si no existe previamente.
     * Esta operacion es thread-safe y no bloqueante.
     * 
     * @param ctx Contexto del cliente espectador a registrar. Si es null, no se realiza ninguna accion.
     */
    public void addSpectator(ClientContext ctx) {
        if (ctx != null) all.addIfAbsent(ctx);
    }

    /**
     * Elimina una sesion del registro.
     * 
     * Remueve el contexto de cliente especificado de la lista de sesiones activas.
     * Debe invocarse cuando un cliente se desconecta del servidor.
     * 
     * @param ctx Contexto del cliente a eliminar. Si es null, no se realiza ninguna accion.
     */
    public void remove(ClientContext ctx) {
        if (ctx != null) all.remove(ctx);
    }

    /**
     * Obtiene una copia inmutable de todas las sesiones activas.
     * 
     * Retorna un snapshot del estado actual del registro de sesiones que puede
     * iterarse de forma segura sin preocupacion por modificaciones concurrentes.
     * Util para consolas de administracion y monitoreo.
     * 
     * @return Lista inmutable con todas las sesiones de clientes activas
     */
    public List<ClientContext> list() {
        return Collections.unmodifiableList(all);
    }
}
