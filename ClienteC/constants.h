#ifndef CONSTANTS_H
#define CONSTANTS_H

// --- Red (Servidor) ---
#define DKJ_SERVER_HOST "127.0.0.1"
#define DKJ_SERVER_PORT 5555

// --- Protocolo ---
#define DKJ_MAX_LINE 1024
#define DKJ_MSG_HELLO_PLAYER    "HELLO PLAYER\n"
#define DKJ_MSG_HELLO_SPECTATOR "HELLO SPECTATOR\n"
#define DKJ_MSG_PING            "PING\n"
#define DKJ_MSG_BYE             "BYE\n"

// --- Mundo (GUI “como juego”) ---
#define DKJ_LIANAS       6       // columnas
#define DKJ_HEIGHT_MAX   12      // 0..12 (0 = piso)

// --- Estética ---
#define WIN_W 800
#define WIN_H 600
#define MARGIN_X 80
#define MARGIN_Y 60
#define JR_RADIUS  10
#define RED_SIZE   18
#define BLUE_SIZE  14
#define FRUIT_SIZE 10

#endif
