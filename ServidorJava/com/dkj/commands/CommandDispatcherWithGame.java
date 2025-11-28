package com.dkj.commands;

import com.dkj.game.Game;
import com.dkj.model.Direction;
import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.PlayerId;
import com.dkj.model.Points;
import com.dkj.model.Role;
import com.dkj.server.ClientContext;
import com.dkj.server.MatchRegistry;
import com.dkj.server.SessionRegistry;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dispatcher principal encargado de interpretar y procesar los comandos de la
 * red para clientes jugadores, espectadores y administradores.
 *
 * <p>La instancia mantiene referencias a los registros de partidas y sesiones
 * activas, y expone un mecanismo de despacho que traduce cada linea recibida en
 * una invocacion concreta al dominio del juego. Este componente es el puente
 * entre el protocolo textual y las operaciones del modelo.</p>
 */
public final class CommandDispatcherWithGame {

    @SuppressWarnings("unused")
    private final String serverId;
    private final MatchRegistry registry;
    private final SessionRegistry sessions;
    private Map<String, Function<Call, String>> table;

    /**
     * Wrapper liviano que agrupa los tokens de un comando junto con el contexto
     * del cliente que lo emitio. Se utiliza internamente para facilitar el paso de
     * datos entre los handlers privados.
     */
    private static final class Call {
        final List<String> tokens;
        final ClientContext ctx;

        Call(List<String> t, ClientContext c){ this.tokens = t; this.ctx = c; }
    }

    /**
     * Crea un nuevo dispatcher enlazado a los registros dados.
     *
     * @param serverId identificador textual del servidor (util para trazas y
     *                 comandos administrativos)
     * @param registry registro de partidas activas que permite obtener juegos y
     *                 validar conexiones
     * @param sessions registro de sesiones para reflejar nuevos jugadores o
     *                 espectadores conectados
     */
    public CommandDispatcherWithGame(String serverId, MatchRegistry registry, SessionRegistry sessions) {
        this.serverId  = Objects.requireNonNull(serverId);
        this.registry  = Objects.requireNonNull(registry);
        this.sessions  = Objects.requireNonNull(sessions);
        initMaps();
    }

    /**
     * Inicializa la tabla de despacho asignando cada palabra clave a su handler
     * correspondiente. Se invoca desde el constructor y no se expone al exterior.
     */
    private void initMaps() {
        Map<String, Function<Call, String>> t = new HashMap<>();
        t.put("PING",  this::onPing);
        t.put("HELLO", this::onHello);
        t.put("MOVE",  this::onMove);
        t.put("WIN",   this::onWin);
        t.put("BYE",   this::onBye);
        t.put("ADMIN", this::onAdmin);
        this.table = Collections.unmodifiableMap(t);
    }

    /**
     * Punto de entrada publico que recibe una linea del cliente y devuelve la
     * respuesta protocolaria correspondiente.
     *
     * @param line comando textual enviado por el cliente (puede incluir
     *             parametros separados por espacios)
     * @param ctx  contexto del cliente que emite el comando
     * @return respuesta procesada, en el formato del protocolo (OK/ACK/ERR/...) 
     */
    public String dispatch(String line, ClientContext ctx) {
        if (line == null || line.isBlank()) return err(Integer.valueOf(400), "Comando vacio. Escribe un comando valido (ej: PING, HELLO, MOVE).");
        final List<String> tokens = tokenize(line);
        final String head = tokens.get(Integer.valueOf(0)).toUpperCase(Locale.ROOT);
        var fn = table.get(head);
        return (fn == null) ? onUnknown(new Call(tokens, ctx)) : fn.apply(new Call(tokens, ctx));
    }

    // -------------------- Cliente --------------------

    /**
     * Atiende el comando PING enviado por clientes para validar conectividad.
     *
     * @param c wrapper con tokens del comando y contexto del cliente
     * @return "PONG" si la sintaxis es correcta, o un error 400 en caso contrario
     */
    private String onPing(Call c) {
        return (c.tokens.size()==Integer.valueOf(1)) ? "PONG" : err(Integer.valueOf(400),"PING no requiere parametros. Uso correcto: PING");
    }

    /**
     * Gestiona el comando HELLO, responsable del proceso de autenticacion y
     * registro de jugadores/espectadores.
     *
     * @param c wrapper con tokens y contexto del cliente
     * @return respuesta OK con datos de session o mensaje de error detallado
     */
    private String onHello(Call c) {
        // Formatos:
        // HELLO PLAYER
        // HELLO SPECTATOR <PLAYER_ID>
        if (c.tokens.size() < Integer.valueOf(2)) return err(Integer.valueOf(400),"Comando HELLO incompleto. Uso: HELLO PLAYER o HELLO SPECTATOR <PLAYER_ID>");
        String role = c.tokens.get(Integer.valueOf(1)).toUpperCase(Locale.ROOT);

        if (role.equals("PLAYER")) {
            if (!registry.canAddPlayer()) return err(Integer.valueOf(409),"Servidor lleno. No se pueden agregar mas jugadores (maximo: 2 jugadores activos).");
            var pid = new PlayerId();
            c.ctx.playerId(pid);
            c.ctx.role(Role.PLAYER);
            sessions.addPlayer(c.ctx);
            registry.createRoomFor(pid, c.ctx.out());
            return "OK " + pid.value();
        }

        if (role.equals("SPECTATOR")) {
            if (c.tokens.size() != Integer.valueOf(3)) return err(Integer.valueOf(400),"Falta el ID del jugador. Uso: HELLO SPECTATOR <PLAYER_INDEX>");
            
            String indexOrId = c.tokens.get(Integer.valueOf(2));
            Boolean ok = Boolean.FALSE;
            PlayerId targetId = null;
            
            // Intentar parsear como índice (1, 2, etc)
            try {
                int index = Integer.parseInt(indexOrId);
                targetId = registry.getPlayerIdByIndex(index);
                if (targetId != null) {
                    ok = registry.attachSpectatorByIndex(index, c.ctx.out());
                    if (!ok) {
                        // El jugador existe pero la sala está llena
                        return err(Integer.valueOf(403), "Sala de espectadores llena. El jugador " + index + " ya tiene el maximo de espectadores (maximo: 2).");
                    }
                } else {
                    // El jugador no existe
                    return err(Integer.valueOf(404), "Jugador " + index + " no encontrado. Verifica que el jugador este conectado.");
                }
            } catch (NumberFormatException e) {
                // Si no es un número, intentar como PlayerId directo
                targetId = new PlayerId(indexOrId);
                ok = registry.attachSpectatorTo(targetId, c.ctx.out());
                if (!ok) return err(Integer.valueOf(409), "Jugador no encontrado o sala de espectadores llena (maximo: 2 espectadores por jugador).");
            }
            
            c.ctx.role(Role.SPECTATOR);
            sessions.addSpectator(c.ctx);
            return "OK SPECTATOR " + (targetId != null ? targetId.value() : indexOrId);
        }

        return err(Integer.valueOf(422),"Rol invalido. Debe ser PLAYER o SPECTATOR. Uso: HELLO PLAYER o HELLO SPECTATOR <ID>");
    }

    /**
     * Procesa movimientos del jugador en la partida asociada.
     *
     * @param c wrapper con tokens y contexto
     * @return ACK con la direccion aplicada o mensaje de error indicando la causa
     */
    private String onMove(Call c) {
        if (c.tokens.size()!=Integer.valueOf(2)) return err(Integer.valueOf(400),"Comando MOVE incompleto. Uso: MOVE <direccion> (UP, DOWN, LEFT, RIGHT, JUMP)");
        if (c.ctx.role() != Role.PLAYER) return err(Integer.valueOf(403),"Solo los jugadores pueden moverse. Los espectadores no pueden usar el comando MOVE.");
        if (c.ctx.playerId() == null)    return err(Integer.valueOf(403),"No estas autenticado. Usa HELLO PLAYER primero para conectarte como jugador.");

        Direction dir = Direction.fromString(c.tokens.get(Integer.valueOf(1)));
        if (dir == null) return err(Integer.valueOf(422),"Direccion '" + c.tokens.get(Integer.valueOf(1)) + "' invalida. Usa: UP, DOWN, LEFT, RIGHT o JUMP");

        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(Integer.valueOf(404),"Sala de juego no encontrada. Reconectate con HELLO PLAYER.");

        game.enqueueMove(c.ctx.playerId(), dir);
        return "ACK MOVE " + dir.name();
    }

    /**
     * Atiende la notificacion de victoria enviada por el cliente jugador.
     *
     * @param c wrapper con tokens y contexto del cliente
     * @return "OK VICTORY" si la solicitud es valida o un mensaje de error
     */
    private String onWin(Call c) {
        if (c.ctx.role() != Role.PLAYER || c.ctx.playerId() == null) {
            return err(Integer.valueOf(403), "Solo los jugadores pueden ganar. Los espectadores no pueden usar WIN.");
        }
        var game = registry.gameOf(c.ctx.playerId());
        if (game == null) return err(Integer.valueOf(404), "Sala de juego no encontrada. Reconectate con HELLO PLAYER.");
        
        game.handleVictory(c.ctx.playerId());
        return "OK VICTORY";
    }

    /**
     * Gestiona la desconexion voluntaria del cliente mediante BYE.
     *
     * @param c wrapper con tokens y contexto
     * @return "BYE" si el comando es correcto o error 400 en caso contrario
     */
    private String onBye(Call c) {
        return (c.tokens.size()==Integer.valueOf(1)) ? "BYE" : err(Integer.valueOf(400),"BYE no requiere parametros. Uso correcto: BYE");
    }

    // -------------------- Admin ---------------------
    // Sintaxis obligatoria para escoger partida:
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>
    //   ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>
    //   ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>
    //   ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>
    /**
     * Ejecuta comandos administrativos sobre partidas en curso (SPAWN/DELETE).
     *
     * @param c wrapper con los tokens ya tokenizados y el contexto de cliente
     * @return respuesta del comando administrativo en formato ACK/ERR
     */
    private String onAdmin(Call c) {
        var tk = c.tokens;
        if (tk.size() < Integer.valueOf(3)) return err(Integer.valueOf(400),"Comando ADMIN incompleto. Uso: ADMIN <PLAYER_ID> <SPAWN|DELETE> ... (usa 'help' en la consola admin para ver ejemplos)");

        var target = new PlayerId(tk.get(Integer.valueOf(1)));
        var game = registry.gameOf(target);
        if (game == null) return err(Integer.valueOf(404),"Jugador '" + tk.get(Integer.valueOf(1)) + "' no encontrado o no tiene sala activa. Verifica el ID con el comando 'list' en la consola admin.");

        String sub = tk.get(Integer.valueOf(2)).toUpperCase(Locale.ROOT);
        return switch (sub) {
            case "SPAWN"  -> onAdminSpawn(game, tk);
            case "DELETE" -> onAdminDelete(game, tk);
            default       -> err(Integer.valueOf(400),"Subcomando ADMIN '" + sub + "' invalido. Debe ser SPAWN o DELETE.");
        };
    }

    /**
     * Handler auxiliar para el subcomando ADMIN SPAWN, permitiendo generar
     * cocodrilos o frutas en la partida indicada.
     *
     * @param game instancia de juego sobre la cual se operara
     * @param tk   tokens completos del comando administrativo
     * @return ACK si la operacion fue exitosa o un mensaje de error descriptivo
     */
    private String onAdminSpawn(Game game, List<String> tk) {
        if (tk.size()<Integer.valueOf(4)) return err(Integer.valueOf(400),"Comando SPAWN incompleto. Especifica el tipo de entidad: CROCODILE o FRUIT");
        String kind = tk.get(Integer.valueOf(3)).toUpperCase(Locale.ROOT);

        if (kind.equals("CROCODILE")) {
            if (tk.size()<Integer.valueOf(5)) return err(Integer.valueOf(400),"Falta especificar el color del cocodrilo. Uso: SPAWN CROCODILE <RED|BLUE> ...");
            String color = tk.get(Integer.valueOf(4)).toUpperCase(Locale.ROOT);
            if (color.equals("RED")) {
                if (tk.size()!=Integer.valueOf(7)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: SPAWN CROCODILE RED <LIANA> <ALTURA>\nLIANA: 1-7, ALTURA: 0-12");
                String result = game.spawnCrocodileRed(new LianaId(tk.get(Integer.valueOf(5))), new Height(tk.get(Integer.valueOf(6))));
                return result.equals("OK") ? "ACK ADMIN SPAWN CROCODILE RED" : err(Integer.valueOf(422), "Error al crear cocodrilo rojo: " + result);
            } else if (color.equals("BLUE")) {
                // Azul desde admin: necesita LIANA para spawnearlo directo en liana
                if (tk.size()!=Integer.valueOf(6)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: SPAWN CROCODILE BLUE <LIANA>\nLIANA: 1-7");
                String result = game.spawnCrocodileBlueOnLiana(new LianaId(tk.get(Integer.valueOf(5))));
                return result.equals("OK") ? "ACK ADMIN SPAWN CROCODILE BLUE" : err(Integer.valueOf(422), "Error al crear cocodrilo azul: " + result);
            } else {
                return err(Integer.valueOf(422),"Color '" + color + "' invalido para cocodrilo. Usa RED (rojo que patrulla) o BLUE (azul que desciende).");
            }
        } else if (kind.equals("FRUIT")) {
            if (tk.size()!=Integer.valueOf(7)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>\nLIANA: 1-7, ALTURA: 0-12, PUNTOS: valor positivo");
            String result = game.spawnFruit(new LianaId(tk.get(Integer.valueOf(4))), new Height(tk.get(Integer.valueOf(5))), new Points(tk.get(Integer.valueOf(6))));
            return result.equals("OK") ? "ACK ADMIN SPAWN FRUIT" : err(Integer.valueOf(422), "Error al crear fruta: " + result);
        } else {
            return err(Integer.valueOf(422),"Tipo de entidad '" + kind + "' invalido. Usa CROCODILE (cocodrilos) o FRUIT (frutas).");
        }
    }

    /**
     * Handler auxiliar para ADMIN DELETE, utilizado para retirar frutas
     * existentes de una partida.
     *
     * @param game instancia de juego afectada
     * @param tk   tokens del comando administrativo
     * @return ACK si la fruta fue eliminada correctamente o error con detalle
     */
    private String onAdminDelete(Game game, List<String> tk) {
        if (tk.size()<Integer.valueOf(5)) return err(Integer.valueOf(400),"Comando DELETE incompleto. Uso: DELETE FRUIT <LIANA> <ALTURA>");
        String kind = tk.get(Integer.valueOf(3)).toUpperCase(Locale.ROOT);
        if (!kind.equals("FRUIT")) return err(Integer.valueOf(422),"Solo se pueden eliminar FRUIT (frutas). Los cocodrilos no se pueden eliminar con DELETE.");
        if (tk.size()!=Integer.valueOf(6)) return err(Integer.valueOf(400),"Parametros incorrectos. Uso: DELETE FRUIT <LIANA> <ALTURA>\nLIANA: 1-7, ALTURA: 0-12");
        game.deleteFruit(new LianaId(tk.get(Integer.valueOf(4))), new Height(tk.get(Integer.valueOf(5))));
        return "ACK ADMIN DELETE FRUIT";
    }

    /**
     * Fallback utilizado cuando la palabra clave del comando no coincide con
     * ninguna entrada de la tabla de despacho.
     *
     * @param c wrapper con tokens y contexto del cliente
     * @return mensaje de error 400 indicando los comandos disponibles
     */
    private String onUnknown(Call c) {
        String cmd = c.tokens.isEmpty() ? "(vacio)" : c.tokens.get(Integer.valueOf(0));
        return "ERR 400 Comando '" + cmd + "' no reconocido. Comandos validos: PING, HELLO, MOVE, WIN, BYE, ADMIN";
    }

    // -------------------- Util ------------------------------

    /**
     * Divide la linea recibida en tokens separados por espacios, ignorando
     * segmentos en blanco.
     *
     * @param line linea original recibida del cliente
     * @return lista inmutable de tokens en el orden de aparicion
     */
    private static List<String> tokenize(String line){
        return Stream.of(line.trim().split("\\s+"))
                     .filter(s -> !s.isBlank())
                     .collect(Collectors.toList());
    }
    /**
     * Construye respuestas de error siguiendo el formato del protocolo.
     *
     * @param code codigo numerico HTTP-like que identifica la causa
     * @param text descripcion legible del error
     * @return cadena con prefijo ERR lista para enviarse al cliente
     */
    private static String err(Integer code, String text){ return "ERR " + code + " " + text; }
}