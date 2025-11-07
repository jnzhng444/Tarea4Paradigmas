#ifndef CONSTANTS_H
#define CONSTANTS_H

// ============ CONFIGURACIÓN DE RED ============
#define DKJ_SERVER_HOST "127.0.0.1"
#define DKJ_SERVER_PORT 5555

// ============ PROTOCOLO ============
#define DKJ_MAX_LINE 1024
#define DKJ_MSG_HELLO_PLAYER    "HELLO PLAYER\n"
#define DKJ_MSG_HELLO_SPECTATOR "HELLO SPECTATOR\n"
#define DKJ_MSG_PING            "PING\n"
#define DKJ_MSG_BYE             "BYE\n"

// ============ DIMENSIONES DEL MUNDO ============
#define DKJ_LIANAS       6
#define DKJ_HEIGHT_MAX   12

// ============ TAMAÑOS DE ENTIDADES ============
#define PLAYER_WIDTH    20.0f
#define PLAYER_HEIGHT   30.0f
#define JR_SIZE         15
#define RED_CROC_SIZE   28
#define BLUE_CROC_SIZE  24
#define FRUIT_SIZE      8
#define DK_SIZE         40

// NOTA: Las constantes de física (GRAVITY, MOVE_SPEED, etc.) 
// están definidas localmente en update_player_physics() 
// para evitar conflictos con headers de Windows

#endif