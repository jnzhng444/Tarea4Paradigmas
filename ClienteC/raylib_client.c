// Cliente Donkey Kong Jr - Raylib (sprites + HUD + respawn flash + popups de puntos)
#include "raylib.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <ctype.h>
#include <math.h>  

#include "network.h"
#include "constants.h"
#include "types.h"

// ============ DECLARACIONES (level.c) ============
void level_init(Level* lvl);
void level_draw(Level* lvl);
void level_draw_debug(Level* lvl);
void level_set_debug(bool enabled);
bool level_check_ground(Level* lvl, Vector2 pos, float width, float height);
bool level_can_grab_liana(Level* lvl, Vector2 pos, int* outIndex);
void level_check_key(Level* lvl, Vector2 playerPos);
bool level_check_win(Level* lvl, Vector2 playerPos);

// ============ SPRITES GLOBALES (visibles para level.c) ============
Texture2D g_player_sprite = {0};
Texture2D g_red_croc_sprite = {0};
Texture2D g_blue_croc_sprite = {0};
Texture2D g_fruit_sprite = {0};
Texture2D g_dk_sprite = {0};
Texture2D g_mario_sprite = {0};
Texture2D g_key_sprite = {0};

// ============ ESTADO GLOBAL ============
static World g_world = {0};
static Level g_level = {0};
static char g_local_id[64] = "";
static bool g_connected = false;
static bool g_hello_done = false;

// ===== DEBUG / AJUSTES VISUALES =====
static bool  g_debug_draw  = false;
static float g_hb_scale_x  = 0.99f;
static float g_hb_scale_y  = 0.85f;
static float g_feet_offset = 0.0f;

// ===== FEEDBACK / EFECTOS =====
static double g_respawn_flash_until = 0.0;

// ===== DIRECCIÓN DEL JUGADOR =====
static float g_last_player_x = -999.0f;

typedef struct {
    bool active;
    Vector2 pos;
    int points;
    double t0;
    double duration;
} PointsPopup;

#define POPUP_MAX 32
static PointsPopup g_popups[POPUP_MAX] = {0};
static bool g_won = false;

static void spawn_points_popup(Vector2 pos, int points) {
    for (int i = 0; i < POPUP_MAX; i++) {
        if (!g_popups[i].active) {
            g_popups[i].active   = true;
            g_popups[i].pos      = pos;
            g_popups[i].points   = points;
            g_popups[i].t0       = GetTime();
            g_popups[i].duration = 0.6;
            return;
        }
    }
}

static volatile bool g_world_lock = false;
static void lock_world(void)   { while (g_world_lock) { } g_world_lock = true; }
static void unlock_world(void) { g_world_lock = false; }

// ============ HITBOX DEL JUGADOR ============
static Rectangle player_hitbox(const PlayerState* p) {
    float w = (float)g_player_sprite.width  * g_hb_scale_x;
    float h = (float)g_player_sprite.height * g_hb_scale_y;
    return (Rectangle){ p->pos.x - w*0.5f, p->pos.y - h*0.5f, w, h };
}

// ============ HELPERS DE MAPEOS ============
static inline float liana_to_x(int l) { 
    // Usar las posiciones reales de las lianas desde level.c
    static const float liana_positions[] = {0, 100, 220, 340, 460, 540, 700, 220}; // índice 0 no usado, 1-7 son las lianas
    if (l < 1) l = 1; 
    if (l > 7) l = 7;
    return liana_positions[l];
}
static inline float height_to_y(int h){ 
    // Mapea altura lógica (0-12) a píxeles del rango recortado (520-120)
    // h=0 -> y=520 (abajo), h=12 -> y=120 (arriba)
    float min_y = 120.0f;  // Top de la liana recortada
    float max_y = 520.0f;  // Bottom de la liana recortada
    float range = max_y - min_y;  // 400 píxeles
    float result = max_y - (h / 12.0f) * range;
    printf("[height_to_y] h=%d -> y=%.1f\n", h, result);
    return result;
}

// ============ PARSING HELPERS ============
static float parse_float_loose(const char* s) {
    while (*s && !isdigit((unsigned char)*s) && *s!='-' && *s!='.') s++;
    return (float)atof(s);
}
static int parse_int_loose(const char* s) {
    while (*s && !isdigit((unsigned char)*s) && *s!='-') s++;
    return atoi(s);
}

typedef struct {
    Vector2 pos;
    int points;
    bool collected;
    bool valid;
} FruitPrev;

static void detect_fruit_events(FruitPrev* prev, int prevCount) {
    const float TOL = 2.0f;
    bool seenPrev[32] = {0};

    for (int i = 0; i < g_world.fruitCount; i++) {
        Fruit cur = g_world.fruits[i];
        int matched = -1;
        for (int j = 0; j < prevCount; j++) {
            if (!prev[j].valid) continue;
            if (fabsf(prev[j].pos.x - cur.pos.x) <= TOL &&
                fabsf(prev[j].pos.y - cur.pos.y) <= TOL) {
                matched = j; break;
            }
        }
        if (matched >= 0) {
            seenPrev[matched] = true;
            if (!prev[matched].collected && cur.collected) {
                spawn_points_popup(cur.pos, cur.points);
            }
        }
    }

    for (int j = 0; j < prevCount; j++) {
        if (!prev[j].valid) continue;
        if (!seenPrev[j]) {
            if (!prev[j].collected) {
                spawn_points_popup(prev[j].pos, prev[j].points);
            }
        }
    }
}

// ============ PARSING ============

static void parse_players_block(const char* start, const char* end) {
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf)-1;
    memcpy(buf, start, len); buf[len] = '\0';

    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item) {
        char idbuf[64] = {0};
        const char* id_ptr = strstr(item, "id=");
        if (id_ptr) {
            id_ptr += 3;
            int i = 0; while (id_ptr[i] && id_ptr[i]!=',' && i<63) { idbuf[i]=id_ptr[i]; i++; }
            idbuf[i] = '\0';
        }

        if (idbuf[0] && g_local_id[0] && strcmp(idbuf, g_local_id)==0) {
            PlayerState* p = &g_world.players[0];
            bool was_initialized = (g_world.playerCount > 0);
            bool prev_facing = was_initialized ? p->facingRight : true;
            
            memset(p, 0, sizeof(*p));
            strncpy(p->id, idbuf, sizeof(p->id)-1);

            const char* x_ptr = strstr(item, "x=");
            const char* y_ptr = strstr(item, "y=");
            const char* liana_ptr = strstr(item, "onLiana=");
            const char* score_ptr = strstr(item, "score=");
            const char* lives_ptr = strstr(item, "lives=");
            const char* respawn_ptr = strstr(item, "respawned=");

            float new_x = 0.0f;
            if (x_ptr)      new_x = p->pos.x = parse_float_loose(x_ptr+2);
            if (y_ptr)      p->pos.y = parse_float_loose(y_ptr+2);
            if (liana_ptr)  p->onLiana = (parse_float_loose(liana_ptr+8) > 0.5f);
            if (score_ptr)  p->score = (int)parse_float_loose(score_ptr+6);
            if (lives_ptr)  p->lives = (int)parse_float_loose(lives_ptr+6);
            if (respawn_ptr) {
                p->respawned = (parse_float_loose(respawn_ptr+10) > 0.5f);
                if (p->respawned) {
                    g_respawn_flash_until = GetTime() + 0.35;
                    // Resetear la llave cuando mueres
                    g_level.hasKey = false;
                }
            }

            // Actualizar dirección basada en movimiento
            if (g_last_player_x > -990.0f) {
                float dx = new_x - g_last_player_x;
                if (fabsf(dx) > 0.5f) {  // Solo actualizar si hay movimiento significativo
                    p->facingRight = (dx > 0);
                } else {
                    p->facingRight = prev_facing;  // Mantener dirección anterior
                }
            } else {
                p->facingRight = true;  // Por defecto mirando a la derecha
            }
            g_last_player_x = new_x;

            g_world.playerCount = 1;
            break;
        }
        item = strtok_r(NULL, "|", &saveptr);
    }
}

static void parse_reds_block(const char* start, const char* end) {
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf)-1;
    memcpy(buf, start, len); buf[len] = '\0';

    g_world.redCount = 0;
    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item && g_world.redCount < MAX_REDS) {
        int l = 0, h = 0;
        int goingUp = 0;
        
        // Parsear l y h
        (void)sscanf(item, " l=%d , h=%d ", &l, &h);
        
        // Buscar goingUp en el string
        const char* goingUp_ptr = strstr(item, "goingUp=");
        if (goingUp_ptr) {
            goingUp = parse_int_loose(goingUp_ptr + 8);
        }
        
        RedCroc* r = &g_world.reds[g_world.redCount];
        r->pos.x = liana_to_x(l);
        r->pos.y = height_to_y(h);
        r->lianaIndex = (l>0) ? (l-1) : 0;
        r->speed = 60.0f;
        r->minH = 0.0f;
        r->maxH = (float)h;
        r->goingUp = (goingUp != 0);
        
        g_world.redCount++;
        item = strtok_r(NULL, "|", &saveptr);
    }
}

static void parse_blues_block(const char* start, const char* end) {
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf)-1;
    memcpy(buf, start, len); buf[len] = '\0';

    printf("[CLIENT PARSE BLUES] Raw data: [%s]\n", buf);

    g_world.blueCount = 0;
    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    
    while (item && g_world.blueCount < MAX_BLUES) {
        BlueCroc* b = &g_world.blues[g_world.blueCount];
        
        printf("[CLIENT] Parsing blue item: [%s]\n", item);
        
        const char* state_ptr = strstr(item, "state=");
        
        if (state_ptr && strncmp(state_ptr + 6, "walking", 7) == 0) {
            const char* x_ptr = strstr(item, "x=");
            const char* h_ptr = strstr(item, "h=");
            
            float x_val = 0.0f;
            int h_val = 12;
            
            if (x_ptr) {
                x_val = parse_float_loose(x_ptr + 2);
                b->pos.x = x_val;
            }
            if (h_ptr) {
                h_val = parse_int_loose(h_ptr + 2);
                b->pos.y = height_to_y(h_val);
            }
            
            b->isWalking = true;
            
            printf("[CLIENT] Blue WALKING: x=%.1f (parsed %.1f), h=%d -> y=%.1f\n", 
                   b->pos.x, x_val, h_val, b->pos.y);
            
        } else {
            int l = 0, h = 0;
            
            const char* l_ptr = strstr(item, "l=");
            const char* h_ptr = strstr(item, "h=");
            
            if (l_ptr) l = parse_int_loose(l_ptr + 2);
            if (h_ptr) h = parse_int_loose(h_ptr + 2);
            
            b->pos.x = liana_to_x(l);
            b->pos.y = height_to_y(h);
            b->lianaIndex = (l > 0) ? (l - 1) : 0;
            
            b->isWalking = false;
            
            printf("[CLIENT] Blue DESCENDING: l=%d h=%d -> x=%.1f y=%.1f\n", 
                   l, h, b->pos.x, b->pos.y);
        }
        
        b->speed = 60.0f;
        b->active = true;
        g_world.blueCount++;
        
        item = strtok_r(NULL, "|", &saveptr);
    }
    
    printf("[CLIENT] Total blues parsed: %d\n", g_world.blueCount);
}

static void parse_fruits_block(const char* start, const char* end, FruitPrev* prev, int prevCount) {
    char buf[2048];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf)-1;
    memcpy(buf, start, len); buf[len] = '\0';

    g_world.fruitCount = 0;
    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item && g_world.fruitCount < MAX_FRUITS) {
        int l=0,h=0,pts=0;
        int col = 0;
        const char* col_ptr = strstr(item, "col=");
        if (col_ptr) col = parse_int_loose(col_ptr+4);

        (void)sscanf(item, " l=%d , h=%d , pts=%d ", &l, &h, &pts);

        Fruit* f = &g_world.fruits[g_world.fruitCount];
        f->pos.x = liana_to_x(l);
        f->pos.y = height_to_y(h);
        f->points = pts;
        f->collected = (col != 0);

        g_world.fruitCount++;
        item = strtok_r(NULL, "|", &saveptr);
    }

    detect_fruit_events(prev, prevCount);
}

static void parse_state_line(const char* line) {
    lock_world();

    FruitPrev prev[32] = {0};
    int prevCount = g_world.fruitCount;
    if (prevCount > 32) prevCount = 32;
    for (int i = 0; i < prevCount; i++) {
        prev[i].valid     = true;
        prev[i].pos       = g_world.fruits[i].pos;
        prev[i].points    = g_world.fruits[i].points;
        prev[i].collected = g_world.fruits[i].collected;
    }

    g_world.playerCount = 0;
    g_world.redCount    = 0;

    const char* pStart = strstr(line, "players=[");
    if (pStart && g_local_id[0]) {
        pStart += 9;
        const char* pEnd = strchr(pStart, ']');
        if (pEnd) parse_players_block(pStart, pEnd);
    }

    const char* rStart = strstr(line, "reds=[");
    if (rStart) {
        rStart += 6;
        const char* rEnd = strchr(rStart, ']');
        if (rEnd) parse_reds_block(rStart, rEnd);
    }

    const char* bStart = strstr(line, "blues=[");
    if (bStart) {
        bStart += 7;
        const char* bEnd = strchr(bStart, ']');
        if (bEnd) {
            char debug[512];
            size_t len = (size_t)(bEnd - bStart);
            if (len < sizeof(debug)) {
                memcpy(debug, bStart, len);
                debug[len] = '\0';
                printf("[DEBUG] Full blues string from server: [%s]\n", debug);
            }
            parse_blues_block(bStart, bEnd);
        }
    }

    const char* fStart = strstr(line, "fruits=[");
    if (fStart) {
        fStart += 8;
        const char* fEnd = strchr(fStart, ']');
        if (fEnd) parse_fruits_block(fStart, fEnd, prev, prevCount);
    }

    unlock_world();
}

// ============ CALLBACKS DE RED ============
static void on_net_message(const char* line) {
    if (!g_hello_done) {
        if (strncmp(line, "OK ", 3) == 0) {
            const char* p = line + 3;
            size_t L = 0; while (p[L] && p[L]!='\r' && p[L]!='\n' && L<sizeof(g_local_id)-1) L++;
            memcpy(g_local_id, p, L); g_local_id[L]='\0';
            g_hello_done = true; g_connected = true;
            TraceLog(LOG_INFO, "Connected as: %s", g_local_id);
            return;
        }
        if (strncmp(line, "ERR", 3) == 0) { TraceLog(LOG_ERROR, "Server error: %s", line); g_connected=false; return; }
    }
    if      (strncmp(line, "STATE ", 6)==0) parse_state_line(line);
    else if (strncmp(line, "PONG",  4)==0) TraceLog(LOG_DEBUG, "PONG received");
    else if (strncmp(line, "ACK",   3)==0) TraceLog(LOG_DEBUG, "ACK: %s", line);
}
static void on_net_disconnect(void) { TraceLog(LOG_WARNING, "Disconnected from server"); g_connected=false; }

// ============ INPUT (REPEAT) ============
static double g_key_hold_time[5] = {0};
static double g_repeat_timers[4] = {0};
static const double INITIAL_DELAY = 0.15;
static const double REPEAT_RATE  = 0.05;

typedef enum { INPUT_LEFT=0, INPUT_RIGHT, INPUT_UP, INPUT_DOWN, INPUT_JUMP } InputKey;

static void process_input(void) {
    double dt = GetFrameTime();
    if (IsKeyDown(KEY_LEFT) || IsKeyDown(KEY_A)) {
        g_key_hold_time[INPUT_LEFT] += dt;
        if (g_key_hold_time[INPUT_LEFT] < INITIAL_DELAY) {
            if (g_key_hold_time[INPUT_LEFT] <= dt) net_send("MOVE LEFT\n");
        } else { g_repeat_timers[INPUT_LEFT] += dt; if (g_repeat_timers[INPUT_LEFT] >= REPEAT_RATE) { net_send("MOVE LEFT\n"); g_repeat_timers[INPUT_LEFT]=0; } }
    } else { g_key_hold_time[INPUT_LEFT]=0; g_repeat_timers[INPUT_LEFT]=0; }
    if (IsKeyDown(KEY_RIGHT) || IsKeyDown(KEY_D)) {
        g_key_hold_time[INPUT_RIGHT] += dt;
        if (g_key_hold_time[INPUT_RIGHT] < INITIAL_DELAY) {
            if (g_key_hold_time[INPUT_RIGHT] <= dt) net_send("MOVE RIGHT\n");
        } else { g_repeat_timers[INPUT_RIGHT] += dt; if (g_repeat_timers[INPUT_RIGHT] >= REPEAT_RATE) { net_send("MOVE RIGHT\n"); g_repeat_timers[INPUT_RIGHT]=0; } }
    } else { g_key_hold_time[INPUT_RIGHT]=0; g_repeat_timers[INPUT_RIGHT]=0; }
    if (IsKeyDown(KEY_UP) || IsKeyDown(KEY_W)) {
        g_key_hold_time[INPUT_UP] += dt;
        if (g_key_hold_time[INPUT_UP] < INITIAL_DELAY) {
            if (g_key_hold_time[INPUT_UP] <= dt) net_send("MOVE UP\n");
        } else { g_repeat_timers[INPUT_UP] += dt; if (g_repeat_timers[INPUT_UP] >= REPEAT_RATE) { net_send("MOVE UP\n"); g_repeat_timers[INPUT_UP]=0; } }
    } else { g_key_hold_time[INPUT_UP]=0; g_repeat_timers[INPUT_UP]=0; }
    if (IsKeyDown(KEY_DOWN) || IsKeyDown(KEY_S)) {
        g_key_hold_time[INPUT_DOWN] += dt;
        if (g_key_hold_time[INPUT_DOWN] < INITIAL_DELAY) {
            if (g_key_hold_time[INPUT_DOWN] <= dt) net_send("MOVE DOWN\n");
        } else { g_repeat_timers[INPUT_DOWN] += dt; if (g_repeat_timers[INPUT_DOWN] >= REPEAT_RATE) { net_send("MOVE DOWN\n"); g_repeat_timers[INPUT_DOWN]=0; } }
    } else { g_key_hold_time[INPUT_DOWN]=0; g_repeat_timers[INPUT_DOWN]=0; }
    if (IsKeyPressed(KEY_SPACE)) net_send("MOVE JUMP\n");
}

// ====== CARGA CON RECORTE DE TRANSPARENCIAS ======
static bool load_texture_cropped(Texture2D* out, const char* path, const char* label) {
    TraceLog(LOG_INFO, "Probe: %s (cwd=%s)", path, GetWorkingDirectory());
    if (!FileExists(path)) return false;

    Image img = LoadImage(path);
    if (!img.data) { TraceLog(LOG_WARNING, "LoadImage failed: %s", path); return false; }

    ImageAlphaCrop(&img, 0.01f);

    *out = LoadTextureFromImage(img);
    UnloadImage(img);

    if (out->id) { TraceLog(LOG_INFO, "%s cargado (recortado) desde: %s", label, path); return true; }
    return false;
}

static void load_sprites(void) {
    bool ok;

    const char* player_paths[] = { "assets/jr_b.png", "output/assets/jr_b.png", "../output/assets/jr_b.png" };
    ok = false; for (int i=0;i<(int)(sizeof(player_paths)/sizeof(player_paths[0]));++i)
        if (load_texture_cropped(&g_player_sprite, player_paths[i], "Jugador")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(20, 30, GREEN); ImageDrawCircle(&img,10,10,8,DARKGREEN);
               g_player_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite de jugador no encontrado, usando procedural"); }

    const char* red_paths[] = { "assets/kremling_red_d.png", "output/assets/kremling_red_d.png", "../output/assets/kremling_red_d.png" };
    ok = false; for (int i=0;i<(int)(sizeof(red_paths)/sizeof(red_paths[0]));++i)
        if (load_texture_cropped(&g_red_croc_sprite, red_paths[i], "Cocodrilo rojo")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(28, 28, RED); ImageDrawRectangle(&img,4,4,20,20,MAROON);
               g_red_croc_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite de cocodrilo rojo no encontrado, usando procedural"); }

    const char* blue_paths[] = { "assets/kremling_blue_d.png", "output/assets/kremling_blue_d.png", "../output/assets/kremling_blue_d.png" };
    ok = false; for (int i=0;i<(int)(sizeof(blue_paths)/sizeof(blue_paths[0]));++i)
        if (load_texture_cropped(&g_blue_croc_sprite, blue_paths[i], "Cocodrilo azul")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(24, 24, BLUE); ImageDrawRectangle(&img,4,4,16,16,DARKBLUE);
               g_blue_croc_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite de cocodrilo azul no encontrado, usando procedural"); }

    const char* fruit_paths[] = { "assets/fruit_bananas.png", "output/assets/fruit_bananas.png", "../output/assets/fruit_bananas.png" };
    ok = false; for (int i=0;i<(int)(sizeof(fruit_paths)/sizeof(fruit_paths[0]));++i)
        if (load_texture_cropped(&g_fruit_sprite, fruit_paths[i], "Fruta")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(16,16,BLANK); ImageDrawCircle(&img,8,8,7,YELLOW);
               ImageDrawCircle(&img,8,8,5,ORANGE);
               g_fruit_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite de fruta no encontrado, usando procedural"); }

    const char* dk_paths[] = { "assets/dk.png", "output/assets/dk.png", "../output/assets/dk.png" };
    ok = false; for (int i=0;i<(int)(sizeof(dk_paths)/sizeof(dk_paths[0]));++i)
        if (load_texture_cropped(&g_dk_sprite, dk_paths[i], "Donkey Kong")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(40,40,BLANK); ImageDrawCircle(&img,20,20,18,BROWN);
               g_dk_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite DK no encontrado, usando procedural"); }

    const char* mario_paths[] = { "assets/mario.png", "output/assets/mario.png", "../output/assets/mario.png" };
    ok = false; for (int i=0;i<(int)(sizeof(mario_paths)/sizeof(mario_paths[0]));++i)
        if (load_texture_cropped(&g_mario_sprite, mario_paths[i], "Mario")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(32,32,BLANK); ImageDrawRectangle(&img,8,8,16,24,RED);
               g_mario_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite Mario no encontrado, usando procedural"); }

    const char* key_paths[] = { "assets/safekey.png", "output/assets/safekey.png", "../output/assets/safekey.png" };
    ok = false; for (int i=0;i<(int)(sizeof(key_paths)/sizeof(key_paths[0]));++i)
        if (load_texture_cropped(&g_key_sprite, key_paths[i], "Safe Key")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(24,16,BLANK); ImageDrawRectangle(&img,4,4,16,8,GOLD);
               g_key_sprite = LoadTextureFromImage(img); UnloadImage(img);
               TraceLog(LOG_WARNING, "Sprite safekey no encontrado, usando procedural"); }

    TraceLog(LOG_INFO, "Carga de sprites completada");
}

static void unload_sprites(void) {
    if (g_player_sprite.id) UnloadTexture(g_player_sprite);
    if (g_red_croc_sprite.id) UnloadTexture(g_red_croc_sprite);
    if (g_blue_croc_sprite.id) UnloadTexture(g_blue_croc_sprite);
    if (g_fruit_sprite.id) UnloadTexture(g_fruit_sprite);
    if (g_dk_sprite.id) UnloadTexture(g_dk_sprite);
    if (g_mario_sprite.id) UnloadTexture(g_mario_sprite);
    if (g_key_sprite.id) UnloadTexture(g_key_sprite);
}

// ====== RENDER ======
static void draw_player(PlayerState* p) {
    Rectangle hb = player_hitbox(p);

    float baseDrawX = hb.x + hb.width  * 0.5f - g_player_sprite.width  * 0.5f;
    float baseDrawY = hb.y + hb.height - g_player_sprite.height + g_feet_offset;

    Rectangle src = {0, 0, (float)g_player_sprite.width, (float)g_player_sprite.height};
    if (!p->facingRight) src.width = -src.width;

    Rectangle dst = {baseDrawX, baseDrawY, (float)g_player_sprite.width, (float)g_player_sprite.height};
    DrawTexturePro(g_player_sprite, src, dst, (Vector2){0,0}, 0.0f, WHITE);

    if (GetTime() < g_respawn_flash_until) {
        DrawCircleV(p->pos, 28.0f, (Color){255, 0, 0, 110});
    }

    if (g_debug_draw) {
        DrawRectangleLinesEx(hb, 1.5f, GREEN);
        DrawCircleLines((int)p->pos.x, (int)p->pos.y, 2, RED);
        DrawLine((int)dst.x, (int)(dst.y + dst.height), (int)(dst.x + dst.width), (int)(dst.y + dst.height), (Color){0,255,255,120});
    }
}

static void draw_red_croc(RedCroc* r) {
    float x = r->pos.x;
    float y = r->pos.y;
    
    // Escala para hacer el cocodrilo más grande
    float scale = 2.0f;
    
    // Determinar rotación basada en dirección de movimiento
    float rotation = r->goingUp ? 180.0f : 0.0f;
    
    // Configurar rectángulos de origen y destino
    Rectangle src = {0, 0, (float)g_red_croc_sprite.width, (float)g_red_croc_sprite.height};
    Rectangle dst = {
        x,  // Centro X
        y,  // Centro Y
        (float)g_red_croc_sprite.width * scale,
        (float)g_red_croc_sprite.height * scale
    };
    
    // El origen es el centro del sprite para que rote correctamente
    Vector2 origin = {
        (float)g_red_croc_sprite.width * scale * 0.5f,
        (float)g_red_croc_sprite.height * scale * 0.5f
    };
    
    DrawTexturePro(g_red_croc_sprite, src, dst, origin, rotation, WHITE);
    
    if (g_debug_draw) {
        DrawCircleLines((int)x, (int)y, 3, RED);
        DrawText(r->goingUp ? "UP" : "DOWN", (int)x + 15, (int)y, 10, RED);
    }
}

static void draw_blue_croc(BlueCroc* b) {
    float x = b->pos.x;
    float y = b->pos.y;
    
    // Escala para hacer el cocodrilo más grande
    float scale = 2.0f;
    
    // Cuando camina: rotar 90 grados y espejear verticalmente
    float rotation = b->isWalking ? 90.0f : 0.0f;
    
    // Configurar rectángulos de origen y destino
    // Para espejear verticalmente cuando camina, usamos alto negativo
    Rectangle src = {
        0, 0, 
        (float)g_blue_croc_sprite.width,
        b->isWalking ? -(float)g_blue_croc_sprite.height : (float)g_blue_croc_sprite.height
    };
    Rectangle dst = {
        x,  // Centro X
        y,  // Centro Y
        (float)g_blue_croc_sprite.width * scale,
        (float)g_blue_croc_sprite.height * scale
    };
    
    // El origen es el centro del sprite para que rote correctamente
    Vector2 origin = {
        (float)g_blue_croc_sprite.width * scale * 0.5f,
        (float)g_blue_croc_sprite.height * scale * 0.5f
        
    };
    
    DrawTexturePro(g_blue_croc_sprite, src, dst, origin, rotation, WHITE);
    
    if (g_debug_draw) {
        DrawCircleLines((int)x, (int)y, 3, SKYBLUE);
        DrawText(b->isWalking ? "WALK" : "DESC", (int)x + 15, (int)y, 10, SKYBLUE);
    }
}

static void draw_popups(void) {
    double tnow = GetTime();
    for (int i = 0; i < POPUP_MAX; i++) {
        if (!g_popups[i].active) continue;
        double t = tnow - g_popups[i].t0;
        if (t >= g_popups[i].duration) { g_popups[i].active = false; continue; }

        float k = (float)(t / g_popups[i].duration);
        float yOffset = -30.0f * k;
        unsigned char alpha = (unsigned char)(255 * (1.0f - k));

        char txt[32];
        snprintf(txt, sizeof(txt), "+%d", g_popups[i].points);
        DrawText(txt,
                 (int)(g_popups[i].pos.x - MeasureText(txt, 20)/2),
                 (int)(g_popups[i].pos.y - 40 + yOffset),
                 20,
                 (Color){255, 255, 0, alpha});
    }
}

static void draw_hud(void) {
    // Score y vidas arriba a la derecha
    if (g_world.playerCount > 0) {
        PlayerState *p = &g_world.players[0];
        char hud2[128]; snprintf(hud2, sizeof(hud2), "Puntos: %d", p->score);
        DrawText(hud2, 680, 10, 18, YELLOW);
        
        char hud3[128]; snprintf(hud3, sizeof(hud3), "Vidas: %d", p->lives);
        DrawText(hud3, 680, 35, 18, LIME);
    }

    // ID abajo a la izquierda
    if (g_local_id[0]) {
        char hud[128]; snprintf(hud, sizeof(hud), "ID: %.20s", g_local_id);
        DrawText(hud, 10, 570, 16, LIME);
    }

    if (!g_connected) { DrawRectangle(0,570,800,30,(Color){150,0,0,200}); DrawText("DESCONECTADO", 300, 575, 20, WHITE); }
}

static void draw_debug_hud(void) {
    if (!g_debug_draw) return;
    DrawRectangle(8, 56, 360, 74, (Color){0,0,0,140});
    char buf[256];
    snprintf(buf, sizeof(buf), "HB_SCALE_X: %.2f  (F7/F8 -/+)", g_hb_scale_x);
    DrawText(buf, 16, 62, 14, LIGHTGRAY);
    snprintf(buf, sizeof(buf), "HB_SCALE_Y: %.2f  (F3/F4 -/+)", g_hb_scale_y);
    DrawText(buf, 16, 78, 14, LIGHTGRAY);
    snprintf(buf, sizeof(buf), "FEET_OFFSET: %.2f  (F5/F6 -/+)", g_feet_offset);
    DrawText(buf, 16, 94, 14, LIGHTGRAY);
}

static void draw_world(void) {
    lock_world(); World w = g_world; unlock_world();

    level_draw(&g_level);
    if (g_debug_draw) level_draw_debug(&g_level);

    for (int i=0;i<w.fruitCount;i++) if (!w.fruits[i].collected) {
        // Escala para hacer las frutas más grandes
        float scale = 1.8f;
        float scaled_w = g_fruit_sprite.width * scale;
        float scaled_h = g_fruit_sprite.height * scale;
        
        Rectangle src = {0, 0, (float)g_fruit_sprite.width, (float)g_fruit_sprite.height};
        Rectangle dst = {
            w.fruits[i].pos.x - scaled_w / 2,
            w.fruits[i].pos.y - scaled_h / 2,
            scaled_w,
            scaled_h
        };
        
        DrawTexturePro(g_fruit_sprite, src, dst, (Vector2){0,0}, 0.0f, WHITE);
    }
    for (int i=0;i<w.blueCount;i++) if (w.blues[i].active) {
        draw_blue_croc(&w.blues[i]);
    }
    for (int i=0;i<w.redCount;i++) {
        draw_red_croc(&w.reds[i]);
    }
    for (int i=0;i<w.playerCount;i++) draw_player(&w.players[i]);

    draw_popups();
    draw_hud();
    draw_debug_hud();
}

// ============ MAIN ============
int main(void) {
    if (net_startup() != 0) { printf("Error: net_startup\n"); return 1; }
    if (net_connect(DKJ_SERVER_HOST, DKJ_SERVER_PORT) != 0) {
        printf("Error: No se pudo conectar a %s:%d\n", DKJ_SERVER_HOST, DKJ_SERVER_PORT);
        net_cleanup(); return 1;
    }
    NetCallbacks cb = { .on_message = on_net_message, .on_disconnect = on_net_disconnect };
    net_start_receiver(cb);
    net_send(DKJ_MSG_HELLO_PLAYER);

    SetConfigFlags(FLAG_VSYNC_HINT);
    InitWindow(800, 600, "Donkey Kong Jr - Raylib");
    SetTargetFPS(60);

    SetTraceLogLevel(LOG_INFO);
    const char* appDir = GetApplicationDirectory();
    ChangeDirectory(appDir);
    TraceLog(LOG_INFO, "CWD now: %s", GetWorkingDirectory());

    load_sprites();
    level_init(&g_level);

    while (!WindowShouldClose()) {
        if (IsKeyPressed(KEY_F1)) { g_debug_draw = !g_debug_draw; if (level_set_debug) level_set_debug(g_debug_draw); }
        if (g_debug_draw) {
            float step = (IsKeyDown(KEY_LEFT_SHIFT) || IsKeyDown(KEY_RIGHT_SHIFT)) ? 0.05f : 0.02f;
            if (IsKeyPressed(KEY_F3)) g_hb_scale_y -= step;
            if (IsKeyPressed(KEY_F4)) g_hb_scale_y += step;
            if (IsKeyPressed(KEY_F5)) g_feet_offset -= 1.0f;
            if (IsKeyPressed(KEY_F6)) g_feet_offset += 1.0f;
            if (IsKeyPressed(KEY_F7)) g_hb_scale_x -= step;
            if (IsKeyPressed(KEY_F8)) g_hb_scale_x += step;
            if (g_hb_scale_x < 0.2f) g_hb_scale_x = 0.2f;
            if (g_hb_scale_y < 0.2f) g_hb_scale_y = 0.2f;
        }

        if (IsKeyPressed(KEY_ESCAPE)) { net_send(DKJ_MSG_BYE); break; }

        if (g_connected && g_hello_done && !g_won) process_input();

        lock_world(); 
        bool havePlayer = (g_world.playerCount > 0);
        Vector2 myPos = havePlayer ? g_world.players[0].pos : (Vector2){0,0};
        int currentScore = havePlayer ? g_world.players[0].score : 0;
        unlock_world();
        
        // Si estábamos en victoria y el score se resetea a 0, significa que el juego se reseteo
        static int prevScore = 0;
        if (g_won && prevScore > 0 && currentScore == 0) {
            g_won = false;
            TraceLog(LOG_INFO, "Game reset detected, can move again!");
        }
        prevScore = currentScore;

        if (havePlayer && !g_won) {
            // Verificar si recoge la llave
            level_check_key(&g_level, myPos);
            
            // Verificar victoria (requiere llave y estar en miniplataforma)
            if (level_check_win(&g_level, myPos)) {
                if (!g_won) {
                    // Enviar comando WIN al servidor
                    net_send("WIN\n");
                    TraceLog(LOG_INFO, "¡Victoria! Nueva vida obtenida y dificultad aumentada!");
                    
                    // Resetear la llave para poder volver a jugar
                    g_level.hasKey = false;
                    
                    g_won = true;
                }
            } else {
                // Resetear won cuando el jugador sale de la zona de victoria
                g_won = false;
            }
        }

        BeginDrawing();
        ClearBackground((Color){30,30,30,255});
        draw_world();
        EndDrawing();
    }

    unload_sprites();
    net_stop_receiver();
    net_disconnect();
    net_cleanup();
    CloseWindow();
    return 0;
}
