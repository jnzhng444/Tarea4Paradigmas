/**
 * @file constants.h
 * @brief Constantes globales del cliente Donkey Kong Jr
 * 
 * Define todas las constantes de configuracion utilizadas por el cliente,
 * incluyendo parametros de red, dimensiones del juego y limites de capacidad.
 * 
 * Categorias:
 * - Configuracion de red: Host y puerto del servidor
 * - Protocolo: Mensajes y limites de comunicacion
 * - Dimensiones del mundo: Lianas y alturas
 * - Tamanos de entidades: Sprites y hitboxes
 * - Capacidades: Limites maximos de arrays
 */

#ifndef CONSTANTS_H
#define CONSTANTS_H

// ============ CONFIGURACION DE RED ============

/**
 * Direccion IP o hostname del servidor de juego.
 * Por defecto: localhost (127.0.0.1)
 */
#define DKJ_SERVER_HOST "127.0.0.1"

/**
 * Puerto TCP donde escucha el servidor de juego.
 * Por defecto: 5555
 */
#define DKJ_SERVER_PORT 5555

// ============ PROTOCOLO ============

/**
 * Longitud maxima de una linea de texto en el protocolo de red (bytes).
 * Limita el tamano de comandos y respuestas.
 */
#define DKJ_MAX_LINE 1024

/**
 * Mensaje de saludo para conectarse como jugador activo.
 */
#define DKJ_MSG_HELLO_PLAYER    "HELLO PLAYER\n"

/**
 * Mensaje de saludo para conectarse como espectador.
 */
#define DKJ_MSG_HELLO_SPECTATOR "HELLO SPECTATOR\n"

/**
 * Mensaje de ping para verificar conexion con el servidor.
 */
#define DKJ_MSG_PING            "PING\n"

/**
 * Mensaje de despedida para cerrar la conexion con el servidor.
 */
#define DKJ_MSG_BYE             "BYE\n"

// ============ DIMENSIONES DEL MUNDO ============

/**
 * Numero total de lianas en el nivel.
 * Incluye 6 lianas principales + 1 mini liana para la llave.
 */
#define DKJ_LIANAS       7

/**
 * Altura maxima logica del juego (coordenadas discretas).
 * Rango de altura: 0 (suelo) a 12 (tope)
 */
#define DKJ_HEIGHT_MAX   12

// ============ TAMANOS DE ENTIDADES ============

/**
 * Ancho del hitbox del jugador en pixeles.
 */
#define PLAYER_WIDTH    20.0f

/**
 * Alto del hitbox del jugador en pixeles.
 */
#define PLAYER_HEIGHT   30.0f

/**
 * Tamano del sprite de Jr (jugador) en pixeles.
 */
#define JR_SIZE         15

/**
 * Tamano del sprite del cocodrilo rojo en pixeles.
 */
#define RED_CROC_SIZE   28

/**
 * Tamano del sprite del cocodrilo azul en pixeles.
 */
#define BLUE_CROC_SIZE  24

/**
 * Tamano del sprite de fruta en pixeles.
 */
#define FRUIT_SIZE      8

/**
 * Tamano del sprite de Donkey Kong en pixeles.
 */
#define DK_SIZE         40

// ============ CAPACIDADES (coinciden con types.h) ============

/**
 * Numero maximo de cocodrilos rojos simultaneos en el mundo.
 */
#define MAX_REDS   32

/**
 * Numero maximo de cocodrilos azules simultaneos en el mundo.
 */
#define MAX_BLUES  32

/**
 * Numero maximo de frutas simultaneas en el mundo.
 */
#define MAX_FRUITS 32

// NOTA: Las constantes de fisica (GRAVITY, MOVE_SPEED, etc.)
// se manejan en el servidor o localmente donde corresponda.

#endif
