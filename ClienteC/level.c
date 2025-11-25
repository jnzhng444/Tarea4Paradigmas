/**
 * @file level.c
 * @brief Gestion y renderizado del nivel de Donkey Kong Jr
 * 
 * Implementa la inicializacion, renderizado y logica de colisiones del nivel estatico.
 * El nivel incluye plataformas, lianas, NPCs (DK y Mario), llave y objetivo de victoria.
 * 
 * Responsabilidades:
 * - Inicializar geometria del nivel (plataformas y lianas)
 * - Renderizar nivel con texturas tileables
 * - Detectar colisiones con suelo
 * - Verificar proximidad a lianas
 * - Detectar recoleccion de llave
 * - Verificar condicion de victoria
 * 
 * Sistema de coordenadas:
 * - X: Horizontal, 0 a 800 pixeles (tipicamente)
 * - Y: Vertical, 0 arriba, 600 abajo (tipicamente)
 * 
 * Arquitectura de renderizado:
 * - Texturas tileables para plataformas y lianas
 * - Clipping para tiles parciales en bordes
 * - Sprites estaticos para DK, Mario y llave
 * - Modo debug opcional con hitboxes
 */

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
extern Texture2D g_platform_sprite;
extern Texture2D g_downplatform_sprite;
extern Texture2D g_liana_sprite;

// ============ DEBUG LOCAL ============
static bool s_debug = false;

/**
 * Activa o desactiva el modo debug de visualizacion.
 * 
 * En modo debug, se dibujan hitboxes y lineas de guia adicionales.
 * 
 * @param enabled true para activar debug, false para desactivar
 */
void level_set_debug(bool enabled) { s_debug = enabled; }

/**
 * Inicializa la geometria y elementos del nivel.
 * 
 * Configura todas las plataformas, lianas y posiciones de NPCs del nivel.
 * Las coordenadas deben coincidir con el servidor (Level.java en ServidorJava)
 * para garantizar que la fisica sea consistente.
 * 
 * Estructura del nivel (de abajo hacia arriba):
 * - Nivel 1 (piso): 4 plataformas con downplatform texture
 * - Nivel 2-3: Plataformas intermedias
 * - Nivel 4-5: Plataformas superiores
 * - Miniplataforma: Objetivo final arriba de Mario
 * 
 * Lianas:
 * - 6 lianas principales (indices 0-5) con alturas variables
 * - 1 mini liana (indice 6) para acceder a la llave
 * 
 * NPCs:
 * - DK: Posicionado en plataforma superior
 * - Mario: Al lado de DK
 * - Llave: Arriba de miniplataforma
 * 
 * @param lvl Puntero a estructura Level a inicializar
 */
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

/**
 * Renderiza el nivel completo con texturas.
 * 
 * Dibuja todos los elementos visuales del nivel en orden de fondo a primer plano:
 * 1. Fondo (color solido)
 * 2. Lianas (con tiling vertical)
 * 3. Plataformas (con tiling horizontal, dos texturas diferentes)
 * 4. DK (sprite estatico)
 * 5. Mario (sprite estatico escalado)
 * 6. Llave (si no ha sido recolectada)
 * 7. Miniplataforma de victoria
 * 
 * Tiling de texturas:
 * - Lianas: Repite g_liana_sprite verticalmente
 * - Plataformas: Usa g_downplatform_sprite para nivel 1 (indices 0-3),
 *               g_platform_sprite para el resto
 * - Clipping: Tiles parciales en bordes para ajuste exacto
 * 
 * Fallbacks:
 * - Si no hay textura cargada, usa primitivas de Raylib (rectangulos, circulos)
 * 
 * @param lvl Puntero a estructura Level con geometria inicializada
 */
void level_draw(Level* lvl) {
    // Fondo (se mantiene por compatibilidad con cliente)
    ClearBackground((Color){15, 15, 22, 255});
    
    // Lianas
    for (int i = 0; i < lvl->lianaCount; i++) {
        Liana* l = &lvl->lianas[i];
        
        if (g_liana_sprite.width > 0 && g_liana_sprite.height > 0) {
            // Dibujar liana con textura repetida verticalmente
            float lianaHeight = l->bottomY - l->topY;
            float tileHeight = (float)g_liana_sprite.height;
            float tileWidth = (float)g_liana_sprite.width;
            
            int numTilesY = (int)(lianaHeight / tileHeight) + 1;
            for (int ty = 0; ty < numTilesY; ty++) {
                float drawY = l->topY + ty * tileHeight;
                if (drawY >= l->bottomY) break;
                
                float clipHeight = tileHeight;
                if (drawY + clipHeight > l->bottomY) {
                    clipHeight = l->bottomY - drawY;
                }
                
                Rectangle src = {0, 0, tileWidth, clipHeight};
                Rectangle dst = {l->x - tileWidth/2, drawY, tileWidth, clipHeight};
                DrawTexturePro(g_liana_sprite, src, dst, (Vector2){0,0}, 0.0f, WHITE);
            }
        } else {
            // Fallback: dibujar línea si no hay textura
            DrawLineEx((Vector2){l->x, l->topY}, (Vector2){l->x, l->bottomY}, 4.0f, (Color){139, 90, 43, 255});
        }
    }
    
    // Plataformas
    for (int i = 0; i < lvl->platformCount; i++) {
        Rectangle r = lvl->platforms[i].rect;
        
        // Primeras 4 plataformas (nivel 1 / piso) usan downplatform, las demás usan platform
        Texture2D platformTex = (i < 4) ? g_downplatform_sprite : g_platform_sprite;
        
        if (platformTex.width > 0 && platformTex.height > 0) {
            // Dibujar plataforma con tiles repetidos
            float tileWidth = (float)platformTex.width;
            float tileHeight = (float)platformTex.height;
            
            int numTilesX = (int)(r.width / tileWidth) + 1;
            for (int tx = 0; tx < numTilesX; tx++) {
                float drawX = r.x + tx * tileWidth;
                if (drawX >= r.x + r.width) break;
                
                float clipWidth = tileWidth;
                if (drawX + clipWidth > r.x + r.width) {
                    clipWidth = (r.x + r.width) - drawX;
                }
                
                Rectangle src = {0, 0, clipWidth, tileHeight};
                Rectangle dst = {drawX, r.y, clipWidth, r.height};
                DrawTexturePro(platformTex, src, dst, (Vector2){0,0}, 0.0f, WHITE);
            }
        } else {
            // Fallback: dibujar rectángulo si no hay textura
            DrawRectangleRec(r, (Color){200, 60, 60, 255});
            DrawRectangleLinesEx(r, 2, (Color){255, 100, 100, 255});
        }
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

/**
 * Renderiza overlay de debug sobre el nivel.
 * 
 * Dibuja elementos de ayuda visual para desarrollo:
 * - Cuadricula de 40x40 pixeles
 * - Contornos de hitboxes de plataformas (amarillo)
 * - Limites superior/inferior de lianas (verde)
 * - Radio de victoria alrededor de DK (azul cielo)
 * 
 * Solo se renderiza si s_debug está activado via level_set_debug(true).
 * 
 * @param lvl Puntero a estructura Level con geometria
 */
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

/**
 * Verifica si el jugador esta sobre alguna plataforma.
 * 
 * Detecta colision entre el hitbox del jugador y las plataformas del nivel.
 * Utiliza tolerancia vertical para permitir aterrizaje suave.
 * 
 * Algoritmo:
 * 1. Construye rectangulo del jugador centrado en pos
 * 2. Para cada plataforma, verifica proyeccion horizontal
 * 3. Si hay overlap horizontal, verifica si los pies estan cerca del tope
 * 4. Tolerancia: -2 a +6 pixeles del tope de la plataforma
 * 
 * @param lvl Puntero a estructura Level con plataformas
 * @param pos Posicion del CENTRO del jugador (pixeles)
 * @param width Ancho del hitbox del jugador (pixeles)
 * @param height Alto del hitbox del jugador (pixeles)
 * @return true si esta sobre una plataforma, false en caso contrario
 */
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

/**
 * Encuentra el indice de la liana mas cercana a una coordenada X.
 * 
 * Busca entre todas las lianas del nivel cual tiene su posicion horizontal
 * mas cercana a la coordenada X especificada. Util para determinar a que
 * liana deberia moverse el jugador.
 * 
 * @param lvl Puntero a estructura Level con lianas
 * @param x Coordenada horizontal de referencia (pixeles)
 * @return Indice de la liana mas cercana (0 a lianaCount-1)
 */
int level_find_nearest_liana(Level* lvl, float x) {
    int nearest = 0; float minDist = 999999.0f;
    for (int i = 0; i < lvl->lianaCount; i++) {
        float dist = fabsf(lvl->lianas[i].x - x);
        if (dist < minDist) { minDist = dist; nearest = i; }
    }
    return nearest;
}

/**
 * Verifica si el jugador puede agarrarse a alguna liana.
 * 
 * Detecta si el jugador esta lo suficientemente cerca de alguna liana
 * para poder agarrarse. Considera rango horizontal y limites verticales.
 * 
 * Criterios:
 * - Distancia horizontal < 30 pixeles
 * - Posicion Y entre topY y bottomY de la liana
 * 
 * @param lvl Puntero a estructura Level con lianas
 * @param pos Posicion del jugador (pixeles)
 * @param outIndex Puntero donde escribir el indice de la liana encontrada (output)
 * @return true si puede agarrarse (outIndex contendra el indice), false en caso contrario
 */
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

/**
 * Verifica y procesa la recoleccion de la llave por el jugador.
 * 
 * Detecta colision circular entre el jugador y la llave. Si colisionan
 * y la llave no ha sido recogida aun, marca lvl->hasKey como true.
 * 
 * Colision:
 * - Radio jugador: 15 pixeles
 * - Radio llave: 15 pixeles
 * - Deteccion: CheckCollisionCircles de Raylib
 * 
 * @param lvl Puntero a estructura Level (modifica hasKey si se recoge)
 * @param playerPos Posicion del jugador (pixeles)
 */
void level_check_key(Level* lvl, Vector2 playerPos) {
    if (!lvl->hasKey) {
        if (CheckCollisionCircles(playerPos, 15, lvl->keyPosition, 15)) {
            lvl->hasKey = true;
            TraceLog(LOG_INFO, "¡Llave recogida!");
        }
    }
}

/**
 * Verifica si el jugador ha completado el nivel (condicion de victoria).
 * 
 * Condiciones para ganar:
 * 1. El jugador debe tener la llave (lvl->hasKey == true)
 * 2. Debe estar fisicamente sobre la miniplataforma de victoria
 * 3. Sus pies deben estar tocando la superficie de la plataforma
 * 4. Debe estar por encima de la plataforma (no debajo ni atravesandola)
 * 
 * Verificacion estricta:
 * - Overlap horizontal con winPlatform
 * - Pies del jugador en rango [-1, +2] pixeles del tope de la plataforma
 * - Posicion Y del jugador < tope de la plataforma
 * 
 * @param lvl Puntero a estructura Level con winPlatform y hasKey
 * @param playerPos Posicion del jugador (pixeles)
 * @return true si se cumple condicion de victoria, false en caso contrario
 */
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
