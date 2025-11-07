#include "types.h"
#include "constants.h"
#include "raylib.h"
#include <math.h>

// Inicializa el nivel estilo Donkey Kong Jr
void level_init(Level* lvl) {
    lvl->platformCount = 0;
    lvl->lianaCount = 0;

    // ===== PLATAFORMAS (estilo escalera hacia arriba) =====
    // Piso
    lvl->platforms[lvl->platformCount++] = (Platform){{50, 520, 700, 20}};
    
    // Nivel 1 (dos plataformas laterales)
    lvl->platforms[lvl->platformCount++] = (Platform){{80, 420, 200, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{520, 420, 200, 15}};
    
    // Nivel 2 (tres plataformas)
    lvl->platforms[lvl->platformCount++] = (Platform){{50, 320, 180, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{310, 320, 180, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{570, 320, 180, 15}};
    
    // Nivel 3 (dos plataformas)
    lvl->platforms[lvl->platformCount++] = (Platform){{130, 220, 200, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{470, 220, 200, 15}};
    
    // Nivel 4 (plataforma central más larga)
    lvl->platforms[lvl->platformCount++] = (Platform){{200, 120, 400, 15}};
    
    // Plataforma de DK (meta)
    lvl->platforms[lvl->platformCount++] = (Platform){{300, 40, 200, 20}};

    // ===== LIANAS (6 verticales) =====
    float spacing = 120.0f;
    float startX = 100.0f;
    
    for (int i = 0; i < DKJ_LIANAS; i++) {
        lvl->lianas[i].x = startX + i * spacing;
        lvl->lianas[i].topY = 30.0f;
        lvl->lianas[i].bottomY = 540.0f;
    }
    lvl->lianaCount = DKJ_LIANAS;

    // ===== META: Donkey Kong =====
    lvl->dkPosition = (Vector2){400.0f, 20.0f};
}

// Dibuja el nivel con Raylib
void level_draw(Level* lvl) {
    // Fondo
    ClearBackground((Color){15, 15, 22, 255});
    
    // Lianas (café/verde)
    for (int i = 0; i < lvl->lianaCount; i++) {
        Liana* l = &lvl->lianas[i];
        DrawLineEx(
            (Vector2){l->x, l->topY},
            (Vector2){l->x, l->bottomY},
            4.0f,
            (Color){139, 90, 43, 255}
        );
    }
    
    // Plataformas (rojas/naranjas)
    for (int i = 0; i < lvl->platformCount; i++) {
        Rectangle r = lvl->platforms[i].rect;
        DrawRectangleRec(r, (Color){200, 60, 60, 255});
        DrawRectangleLinesEx(r, 2, (Color){255, 100, 100, 255});
    }
    
    // Donkey Kong (meta) - gorila grande
    Vector2 dk = lvl->dkPosition;
    DrawCircleV(dk, 20, (Color){80, 50, 30, 255});
    DrawText("DK", (int)dk.x - 10, (int)dk.y - 5, 10, WHITE);
}

// Verifica si un jugador está sobre alguna plataforma
bool level_check_ground(Level* lvl, Vector2 pos, float width, float height) {
    Rectangle playerRect = {pos.x - width/2, pos.y, width, height};
    
    for (int i = 0; i < lvl->platformCount; i++) {
        Rectangle plat = lvl->platforms[i].rect;
        
        // El jugador cae sobre la plataforma
        if (playerRect.x + playerRect.width > plat.x &&
            playerRect.x < plat.x + plat.width) {
            
            float feetY = playerRect.y + playerRect.height;
            if (feetY >= plat.y && feetY <= plat.y + 8.0f) {
                return true;
            }
        }
    }
    return false;
}

// Encuentra la liana más cercana a una posición
int level_find_nearest_liana(Level* lvl, float x) {
    int nearest = 0;
    float minDist = 999999.0f;
    
    for (int i = 0; i < lvl->lianaCount; i++) {
        float dist = fabsf(lvl->lianas[i].x - x);
        if (dist < minDist) {
            minDist = dist;
            nearest = i;
        }
    }
    return nearest;
}

// Verifica si el jugador puede agarrarse a una liana
bool level_can_grab_liana(Level* lvl, Vector2 pos, int* outIndex) {
    float grabRange = 30.0f;
    
    for (int i = 0; i < lvl->lianaCount; i++) {
        float dx = fabsf(lvl->lianas[i].x - pos.x);
        
        if (dx < grabRange && 
            pos.y > lvl->lianas[i].topY && 
            pos.y < lvl->lianas[i].bottomY) {
            *outIndex = i;
            return true;
        }
    }
    return false;
}

// Verifica si el jugador llegó a DK
bool level_check_win(Level* lvl, Vector2 playerPos) {
    return CheckCollisionCircles(playerPos, 15, lvl->dkPosition, 25);
}