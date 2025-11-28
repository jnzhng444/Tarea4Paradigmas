package com.dkj.server;

import com.dkj.entities.DefaultEntityFactory;
import com.dkj.game.Game;
import com.dkj.game.GameRoom;
import com.dkj.model.PlayerId;
import com.dkj.model.Role;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro central de las salas de juego activas en el servidor.
 *
 * <p>Mantiene la asociacion entre cada {@link PlayerId} y su {@link GameRoom}
 * correspondiente, lo que permite gestionar partidas, espectadores y limpieza
 * de recursos cuando los clientes se desconectan. El registro aplica un limite
 * superior al numero de jugadores concurrentes admitidos por el servidor.</p>
 */
public final class MatchRegistry {
    private static final Integer MAX_PLAYERS = Integer.valueOf(2);

    // playerId -> GameRoom
    private final Map<PlayerId, GameRoom> rooms = new LinkedHashMap<>();

    // fábrica explícita para que cada sala cree su Game con Factory Method
    private final DefaultEntityFactory factory;

    /**
     * Crea un registro de partidas que emplea la fabrica indicada para instanciar
     * las entidades del dominio.
     *
     * @param factory fabrica que se utilizara al crear nuevas salas de juego
     */
    public MatchRegistry(DefaultEntityFactory factory) {
        this.factory = factory;
    }

    /**
     * Indica si se puede agregar un nuevo jugador al servidor atendiendo al
     * limite configurado.
     *
     * @return {@code true} cuando aun existe cupo disponible, {@code false} en caso contrario
     */
    public Boolean canAddPlayer(){
        return rooms.size() < MAX_PLAYERS;
    }

    /**
     * Crea una sala asociada al jugador indicado y lo registra en su juego.
     *
     * @param pid identificador del jugador que inicia la sala
     * @param out canal de salida para enviar actualizaciones al cliente
     */
    public void createRoomFor(PlayerId pid, PrintWriter out){
        GameRoom room = new GameRoom(factory);   // ★ pasa la fábrica a la sala
        room.attach(out);
        room.game().addPlayer(pid);
        rooms.put(pid, room);
    }

    /**
     * Adjunta el canal de salida de un espectador a la sala del jugador deseado.
     *
     * @param target identificador del jugador cuya sala se quiere observar
     * @param out canal de salida del espectador
     * @return {@code true} si se adjunto correctamente, {@code false} si la sala no existe o esta llena
     */
    public Boolean attachSpectatorTo(PlayerId target, PrintWriter out){
        GameRoom r = rooms.get(target);
        if (r == null) return Boolean.FALSE;
        return r.attachSpectator(out);
    }

    /**
     * Adjunta un espectador utilizando el indice (base 1) del jugador.
     *
     * @param index posicion del jugador en el registro (comienza en 1)
     * @param out canal de salida del espectador
     * @return {@code true} si se adjunto, {@code false} si el indice es invalido o la sala esta llena
     */
    public Boolean attachSpectatorByIndex(int index, PrintWriter out){
        if (index < 1 || index > rooms.size()) return Boolean.FALSE;
        
        // Obtener el jugador en la posición index-1
        int i = 0;
        for (var entry : rooms.entrySet()) {
            if (i == index - 1) {
                GameRoom r = entry.getValue();
                return r.attachSpectator(out);
            }
            i++;
        }
        return Boolean.FALSE;
    }

    /**
     * Obtiene el identificador del jugador ubicado en el indice solicitado.
     *
     * @param index indice base 1 del jugador buscado
     * @return {@link PlayerId} correspondiente o {@code null} si el indice es invalido
     */
    public PlayerId getPlayerIdByIndex(int index){
        if (index < 1 || index > rooms.size()) return null;
        
        int i = 0;
        for (var pid : rooms.keySet()) {
            if (i == index - 1) {
                return pid;
            }
            i++;
        }
        return null;
    }

    /**
     * Recupera el juego asociado a un jugador para atender comandos MOVE o ADMIN.
     *
     * @param pid identificador del jugador
     * @return instancia de {@link Game} o {@code null} si no existe sala asociada
     */
    public Game gameOf(PlayerId pid){
        GameRoom r = rooms.get(pid);
        return (r == null) ? null : r.game();
    }

    /**
     * Elimina al cliente del registro segun su rol actual.
     *
     * <p>Cuando el cliente es jugador se desmonta la sala completa, notificando a
     * los espectadores y deteniendo el juego. Si es espectador, se lo desacopla
     * de cualquier sala en la que este observando.</p>
     *
     * @param ctx contexto del cliente a retirar del registro
     */
    public void removeClient(ClientContext ctx){
        if (ctx == null) return;
        if (ctx.role() == Role.PLAYER && ctx.playerId()!=null){
            GameRoom r = rooms.remove(ctx.playerId());
            if (r != null) {
                // Notificar a los espectadores que el jugador se desconectó
                r.notifyPlayerDisconnected();
                r.detach(ctx.out());
                r.stop();
            }
        } else {
            // spectator: podría estar adjunto a cualquier sala; intentamos quitarlo de todas
            for (var r : rooms.values()){
                r.detach(ctx.out());
            }
        }
    }
}