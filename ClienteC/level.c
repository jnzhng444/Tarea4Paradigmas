#include "types.h"
#include "constants.h"
#include "raylib.h"
#include <math.h>
#include <stdbool.h>

// ============ VARIABLES EXTERNAS DE SPRITES ============
extern Texture2D g_dk_sprite;

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
    lvl->platforms[lvl->platformCount++] = (Platform){{300, 500, 100, 20}};
    lvl->platforms[lvl->platformCount++] = (Platform){{500, 510, 50, 20}};
    lvl->platforms[lvl->platformCount++] = (Platform){{600, 510, 100, 20}};
    // Nivel 1
    lvl->platforms[lvl->platformCount++] = (Platform){{80, 420, 200, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{520, 420, 200, 15}};
    // Nivel 2
    lvl->platforms[lvl->platformCount++] = (Platform){{50, 320, 180, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{310, 320, 180, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{570, 320, 180, 15}};
    // Nivel 3
    lvl->platforms[lvl->platformCount++] = (Platform){{130, 220, 200, 15}};
    lvl->platforms[lvl->platformCount++] = (Platform){{470, 220, 200, 15}};
    // Nivel 4 (meta)
    lvl->platforms[lvl->platformCount++] = (Platform){{200, 120, 400, 15}};
    // Plataforma DK
    lvl->platforms[lvl->platformCount++] = (Platform){{300, 40, 200, 20}};

    // ===== LIANAS =====
    float spacing = 120.0f;
    float startX = 100.0f;
    for (int i = 0; i < DKJ_LIANAS; i++) {
        lvl->lianas[i].x = startX + i * spacing;
        lvl->lianas[i].topY = 30.0f;
        lvl->lianas[i].bottomY = 540.0f;
    }
    lvl->lianaCount = DKJ_LIANAS;

    // ===== META DK =====
    lvl->dkPosition = (Vector2){400.0f, 20.0f};
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

// Verifica si el jugador llegó a DK
bool level_check_win(Level* lvl, Vector2 playerPos) {
    return CheckCollisionCircles(playerPos, 15, lvl->dkPosition, 25);
}
