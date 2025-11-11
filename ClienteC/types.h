#ifndef TYPES_H
#define TYPES_H

#include <stdbool.h>
#include "raylib.h"

// ============ ENTIDADES DEL JUEGO ============

typedef struct {
    char id[64];
    Vector2 pos;
    Vector2 vel;
    bool onGround;
    bool onLiana;
    int lianaIndex;
    int lives;
    int score;
    bool facingRight;
    bool respawned;     // 🔹 nuevo: para feedback de colisión con cocodrilos
} PlayerState;

typedef struct {
    Vector2 pos;
    int lianaIndex;
    float speed;
    float minH, maxH;
    bool goingUp;
} RedCroc;

typedef struct {
    Vector2 pos;
    int lianaIndex;
    float speed;
    bool active;
} BlueCroc;

typedef struct {
    Vector2 pos;
    int points;
    bool collected;
} Fruit;

// ============ NIVEL/MAPA ============

typedef struct {
    Rectangle rect;
} Platform;

typedef struct {
    float x;
    float topY, bottomY;
} Liana;

typedef struct {
    Platform platforms[16];
    int platformCount;
    Liana lianas[6];
    int lianaCount;
    Vector2 dkPosition;
} Level;

// ============ MUNDO (snapshot del servidor) ============

typedef struct {
    PlayerState players[2];
    int playerCount;
    RedCroc reds[32];
    int redCount;
    BlueCroc blues[32];
    int blueCount;
    Fruit fruits[32];
    int fruitCount;
} World;

#endif
