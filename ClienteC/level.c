// level.c
#include "types.h"
#include "constants.h"
#include "raylib.h"
#include <math.h>
#include <stdbool.h>

// ============ VARIABLES EXTERNAS DE SPRITES ============
extern Texture2D g_dk_sprite;
extern Texture2D g_mario_sprite;
extern Texture2D g_key_sprite;

// ============ DEBUG LOCAL ============
static bool s_debug = false;
void level_set_debug(bool enabled) { s_debug = enabled; }

// Inicializa el nivel estilo Donkey Kong Jr
void level_init(Level* lvl) {
    lvl->platformCount = 0;
    lvl->lianaCount = 0;

    // ===== PLATAFORMAS =====
    // Piso
    lvl->platforms[lvl->platformCount++] = (Platform){{50, 520, 200, 20}};
    lvl->platforms[lvl->platformCount++] = (Platform){{330, 500, 100, 20}};
    lvl->platforms[lvl->platformCount++] = (Platform){{475, 510, 90, 20}};
    lvl->platforms[lvl->platformCount++] = (Platform){{600, 510, 100, 20}};
    // Nivel 2
    lvl->platforms[lvl->platformCount++] = (Platform){{240, 340, 180, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{570, 300, 180, 15}};
    // Nivel 3
    lvl->platforms[lvl->platformCount++] = (Platform){{240, 250, 200, 15}};

    // Nivel 4
    lvl->platforms[lvl->platformCount++] = (Platform){{500, 145, 170, 15}};
    // Nivel 5  <-- aquí va la plataforma donde pondremos a DK (meta)
    lvl->platforms[lvl->platformCount++] = (Platform){{40, 130, 500, 15}};
    
    // Miniplataforma arriba de Mario (nivel final)
    lvl->platforms[lvl->platformCount++] = (Platform){{150, 50, 50, 15}};

    // ===== LIANAS PERSONALIZABLES =====
    // Puedes cambiar topY y bottomY de cada liana individualmente
    // IMPORTANTE: Los valores deben coincidir con el servidor (Level.java)
    
    // Liana 0 (ID=1 en servidor, x=100)
    lvl->lianas[0].x = 100.0f;
    lvl->lianas[0].topY = 140.0f;
    lvl->lianas[0].bottomY = 520.0f;
    
    // Liana 1 (ID=2 en servidor, x=220)
    lvl->lianas[1].x = 220.0f;
    lvl->lianas[1].topY = 140.0f;
    lvl->lianas[1].bottomY = 520.0f;
    
    // Liana 2 (ID=3 en servidor, x=340)
    lvl->lianas[2].x = 340.0f;
    lvl->lianas[2].topY = 240.0f;
    lvl->lianas[2].bottomY = 520.0f;
    
    // Liana 3 (ID=4 en servidor, x=460)
    lvl->lianas[3].x = 460.0f;
    lvl->lianas[3].topY = 140.0f;
    lvl->lianas[3].bottomY = 520.0f;
    
    // Liana 4 (ID=5 en servidor, x=580)
    lvl->lianas[4].x = 540.0f;
    lvl->lianas[4].topY = 160.0f;
    lvl->lianas[4].bottomY = 520.0f;
    
    // Liana 5 (ID=6 en servidor, x=700)
    lvl->lianas[5].x = 700.0f;
    lvl->lianas[5].topY = 80.0f;
    lvl->lianas[5].bottomY = 520.0f;
    
    // Liana 6 - Mini liana para acceder a la llave y miniplataforma (ID=7 en servidor)
    lvl->lianas[6].x = 220.0f;
    lvl->lianas[6].topY = 30.0f;
    lvl->lianas[6].bottomY = 90.0f;
    
    lvl->lianaCount = DKJ_LIANAS;

    // ===== META DK =====
    // Colocamos a DK sobre la plataforma 5 (la última añadida arriba),
    // en el extremo izquierdo (+10 píx de margen) y un poco por encima de la plataforma.
    if (lvl->platformCount > 1) {
        Rectangle plat = lvl->platforms[lvl->platformCount - 2].rect;  // Penúltima (nivel 5)
        float dkX = plat.x + 40.0f;   // margen desde el borde izquierdo
        float dkY = plat.y - 20.0f;   // un poco por encima para que se vea sobre la plataforma
        lvl->dkPosition = (Vector2){ dkX, dkY };
    } else {
        // fallback por si acaso
        lvl->dkPosition = (Vector2){40.0f, 110.0f};
    }
    
    // ===== MARIO =====
    // Mario al lado de DK (ajustado para que sus pies estén en la plataforma)
    lvl->marioPosition = (Vector2){lvl->dkPosition.x + 60.0f, lvl->dkPosition.y + 5.0f};
    
    // ===== MINIPLATAFORMA Y LLAVE =====
    lvl->winPlatform = (Rectangle){150, 50, 50, 15};
    lvl->keyPosition = (Vector2){240.0f, 60.0f};  // Arriba de la miniplataforma
    lvl->hasKey = false;
}

// Dibuja el nivel con Raylib (modo normal)
void level_draw(Level* lvl) {
    // Fondo (se mantiene por compatibilidad con cliente)
    ClearBackground((Color){15, 15, 22, 255});
    
    // Lianas
    for (int i = 0; i < lvl->lianaCount; i++) {
        Liana* l = &lvl->lianas[i];
        DrawLineEx((Vector2){l->x, l->topY}, (Vector2){l->x, l->bottomY}, 4.0f, (Color){139, 90, 43, 255});
    }
    
    // Plataformas
    for (int i = 0; i < lvl->platformCount; i++) {
        Rectangle r = lvl->platforms[i].rect;
        DrawRectangleRec(r, (Color){200, 60, 60, 255});
        DrawRectangleLinesEx(r, 2, (Color){255, 100, 100, 255});
    }
    
    // DK (meta)
    Vector2 dk = lvl->dkPosition;
    if (g_dk_sprite.width > 0 && g_dk_sprite.height > 0) {
        float drawX = dk.x - g_dk_sprite.width / 2;
        float drawY = dk.y - g_dk_sprite.height / 2;
        DrawTextureV(g_dk_sprite, (Vector2){drawX, drawY}, WHITE);
    } else {
        DrawCircleV(dk, 20, (Color){80, 50, 30, 255});
        DrawText("DK", (int)dk.x - 10, (int)dk.y - 5, 10, WHITE);
    }
    
    // Mario (al lado de DK)
    Vector2 mario = lvl->marioPosition;
    if (g_mario_sprite.width > 0 && g_mario_sprite.height > 0) {
        float scale = 1.5f;  // Hacer Mario más grande
        float drawX = mario.x - (g_mario_sprite.width * scale) / 2;
        float drawY = mario.y - (g_mario_sprite.height * scale) / 2;
        DrawTextureEx(g_mario_sprite, (Vector2){drawX, drawY}, 0.0f, scale, WHITE);
    } else {
        DrawCircleV(mario, 20, RED);
        DrawText("M", (int)mario.x - 8, (int)mario.y - 5, 10, WHITE);
    }
    
    // Llave (solo si no ha sido recogida)
    if (!lvl->hasKey) {
        Vector2 key = lvl->keyPosition;
        if (g_key_sprite.width > 0 && g_key_sprite.height > 0) {
            float drawX = key.x - g_key_sprite.width / 2;
            float drawY = key.y - g_key_sprite.height / 2;
            DrawTextureV(g_key_sprite, (Vector2){drawX, drawY}, WHITE);
        } else {
            DrawRectangle((int)key.x - 8, (int)key.y - 4, 16, 8, GOLD);
            DrawText("KEY", (int)key.x - 12, (int)key.y - 12, 8, YELLOW);
        }
    }
}

// Overlay de debug del nivel (cuadrícula, marcas, contornos)
void level_draw_debug(Level* lvl) {
    if (!s_debug) return;

    // Cuadrícula cada 40px
    for (int x = 0; x <= 800; x += 40) DrawLine(x, 0, x, 600, (Color){255,255,255,25});
    for (int y = 0; y <= 600; y += 40) DrawLine(0, y, 800, y, (Color){255,255,255,25});

    // Plataformas resaltadas
    for (int i = 0; i < lvl->platformCount; i++) {
        Rectangle r = lvl->platforms[i].rect;
        DrawRectangleLinesEx(r, 2, YELLOW);
    }

    // Lianas resaltadas
    for (int i = 0; i < lvl->lianaCount; i++) {
        Liana* l = &lvl->lianas[i];
        DrawCircleLines((int)l->x, (int)l->topY, 4, GREEN);
        DrawCircleLines((int)l->x, (int)l->bottomY, 4, GREEN);
        DrawLineEx((Vector2){l->x, l->topY}, (Vector2){l->x, l->bottomY}, 1.0f, GREEN);
    }

    // DK radio meta (25px según level_check_win)
    DrawCircleLines((int)lvl->dkPosition.x, (int)lvl->dkPosition.y, 25.0f, SKYBLUE);
}

// Verifica si un jugador está sobre alguna plataforma (pos = CENTRO del hitbox)
bool level_check_ground(Level* lvl, Vector2 pos, float width, float height) {
    Rectangle playerRect = (Rectangle){ pos.x - width * 0.5f, pos.y - height * 0.5f, width, height };
    
    for (int i = 0; i < lvl->platformCount; i++) {
        Rectangle plat = lvl->platforms[i].rect;

        // Proyección horizontal
        if (playerRect.x + playerRect.width > plat.x &&
            playerRect.x < plat.x + plat.width) {

            // Pies del jugador
            float feetY = playerRect.y + playerRect.height;
            // Ventana de aterrizaje (tolerancia)
            if (feetY >= plat.y - 2.0f && feetY <= plat.y + 6.0f) {
                return true;
            }
        }
    }
    return false;
}

// Encuentra la liana más cercana a una posición
int level_find_nearest_liana(Level* lvl, float x) {
    int nearest = 0; float minDist = 999999.0f;
    for (int i = 0; i < lvl->lianaCount; i++) {
        float dist = fabsf(lvl->lianas[i].x - x);
        if (dist < minDist) { minDist = dist; nearest = i; }
    }
    return nearest;
}

// Verifica si el jugador puede agarrarse a una liana
bool level_can_grab_liana(Level* lvl, Vector2 pos, int* outIndex) {
    float grabRange = 30.0f;
    for (int i = 0; i < lvl->lianaCount; i++) {
        float dx = fabsf(lvl->lianas[i].x - pos.x);
        if (dx < grabRange && pos.y > lvl->lianas[i].topY && pos.y < lvl->lianas[i].bottomY) {
            *outIndex = i; return true;
        }
    }
    return false;
}

// Verifica si el jugador recoge la llave
void level_check_key(Level* lvl, Vector2 playerPos) {
    if (!lvl->hasKey) {
        if (CheckCollisionCircles(playerPos, 15, lvl->keyPosition, 15)) {
            lvl->hasKey = true;
            TraceLog(LOG_INFO, "¡Llave recogida!");
        }
    }
}

// Verifica si el jugador llegó a la plataforma de victoria con la llave
bool level_check_win(Level* lvl, Vector2 playerPos) {
    // PRIMERO debe tener la llave
    if (!lvl->hasKey) {
        return false;
    }
    
    // SEGUNDO debe estar físicamente sobre la miniplataforma (con los pies tocándola)
    Rectangle playerRect = (Rectangle){ 
        playerPos.x - 15, 
        playerPos.y - 15, 
        30, 
        30 
    };
    
    // Pies del jugador
    float feetY = playerRect.y + playerRect.height;
    
    // Verificar que esté directamente sobre la miniplataforma
    // Más estricto: los pies deben estar exactamente sobre la superficie
    if (playerRect.x + playerRect.width > lvl->winPlatform.x &&
        playerRect.x < lvl->winPlatform.x + lvl->winPlatform.width) {
        // Tolerancia muy pequeña para detectar que está parado (no flotando cerca)
        if (feetY >= lvl->winPlatform.y - 1.0f && feetY <= lvl->winPlatform.y + 2.0f) {
            // Además verificar que está lo suficientemente por encima de la plataforma
            // (no debajo ni pasando volando)
            if (playerPos.y < lvl->winPlatform.y) {
                return true;
            }
        }
    }
    
    return false;
}
