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
Texture2D g_fruit_sprites[3] = {0};  // 0=bananas, 1=oranges, 2=strawberry
Texture2D g_dk_sprite = {0};
Texture2D g_mario_sprite = {0};
Texture2D g_key_sprite = {0};

// ============ ESTADO GLOBAL ============
static World g_world = {0};
static Level g_level = {0};
static char g_local_id[64] = "";
static bool g_connected = false;
static bool g_hello_done = false;

// ===== MODO DE JUEGO =====
typedef enum {
    MODE_MENU = 0,
    MODE_PLAYER,
    MODE_OBSERVER
} GameMode;

typedef struct {
    GameMode mode;
    char observe_target[64];
    bool waiting_observer_selection;
    char error_message[256];
    double error_message_until;
    bool player_disconnected;
} GameState;

static GameState g_game_state = {
    .mode = MODE_MENU,
    .observe_target = "",
    .waiting_observer_selection = false,
    .error_message = "",
    .error_message_until = 0.0,
    .player_disconnected = false
};

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

    // Revisar frutas actuales vs anteriores
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
            // Si cambió de NO recolectada a recolectada = jugador la agarró (+puntos)
            if (!prev[matched].collected && cur.collected) {
                TraceLog(LOG_WARNING, "FRUIT COLLECTED! +%d (YELLOW)", cur.points);
                spawn_points_popup(cur.pos, cur.points);  // Siempre positivo
            }
        }
    }

    // Frutas que desaparecieron completamente
    for (int j = 0; j < prevCount; j++) {
        if (!prev[j].valid) continue;
        if (!seenPrev[j] && !prev[j].collected) {
            // Desapareció y NO estaba recolectada antes
            // Verificar si existe una fruta collected=true en esa posición ahora
            bool foundCollected = false;
            for (int i = 0; i < g_world.fruitCount; i++) {
                if (fabsf(g_world.fruits[i].pos.x - prev[j].pos.x) <= TOL &&
                    fabsf(g_world.fruits[i].pos.y - prev[j].pos.y) <= TOL &&
                    g_world.fruits[i].collected) {
                    foundCollected = true;
                    break;
                }
            }
            
            if (foundCollected) {
                // La fruta sigue ahí pero collected=true = jugador la agarró
                TraceLog(LOG_WARNING, "🍌 FRUIT COLLECTED (case 2)! +%d (YELLOW)", prev[j].points);
                spawn_points_popup(prev[j].pos, prev[j].points);  // Positivo
            } else {
                // Realmente desapareció = borrada por admin
                TraceLog(LOG_WARNING, "❌ FRUIT DELETED! -%d (RED)", prev[j].points);
                spawn_points_popup(prev[j].pos, -prev[j].points);  // Negativo
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

    g_world.playerCount = 0;  // Resetear contador
    
    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item && g_world.playerCount < 2) {
        char idbuf[64] = {0};
        const char* id_ptr = strstr(item, "id=");
        if (id_ptr) {
            id_ptr += 3;
            int i = 0; while (id_ptr[i] && id_ptr[i]!=',' && i<63) { idbuf[i]=id_ptr[i]; i++; }
            idbuf[i] = '\0';
        }

        // En modo OBSERVER, mostrar todos los jugadores
        // En modo PLAYER, solo mostrar el jugador local
        bool should_parse = false;
        if (g_game_state.mode == MODE_OBSERVER) {
            should_parse = (idbuf[0] != '\0');  // Mostrar cualquier jugador
        } else {
            should_parse = (idbuf[0] && g_local_id[0] && strcmp(idbuf, g_local_id)==0);
        }

        if (should_parse) {
            PlayerState* p = &g_world.players[g_world.playerCount];
            bool was_initialized = false;
            bool prev_facing = true;
            
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

            g_world.playerCount++;
            
            // En modo PLAYER, solo queremos nuestro jugador
            if (g_game_state.mode == MODE_PLAYER) {
                break;
            }
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
        
        // Asignar tipo de fruta basado en posición (hash simple para consistencia)
        // Usamos la combinación de liana y altura para determinar el tipo
        int hash = (l * 13 + h * 7) % 3;
        f->fruitType = hash;  // 0=bananas, 1=oranges, 2=strawberry

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
    if (pStart) {
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
            
            if (g_game_state.mode == MODE_OBSERVER) {
                TraceLog(LOG_INFO, "Connected as SPECTATOR observing: %s", g_game_state.observe_target);
            } else {
                TraceLog(LOG_INFO, "Connected as PLAYER: %s", g_local_id);
            }
            return;
        }
        if (strncmp(line, "ERR", 3) == 0) { 
            TraceLog(LOG_ERROR, "Server error: %s", line);
            
            // Mostrar mensaje amigable según el error
            if (strstr(line, "409") && strstr(line, "Players full")) {
                // Servidor lleno - volver al menú principal
                snprintf(g_game_state.error_message, sizeof(g_game_state.error_message),
                         "Servidor lleno - Maximo 2 jugadores simultaneos");
                TraceLog(LOG_WARNING, "[PLAYER] %s", g_game_state.error_message);
                g_game_state.error_message_until = GetTime() + 5.0;
                g_connected=false;
                net_disconnect();
                g_game_state.mode = MODE_MENU;
                return;
            } else if (strstr(line, "404") || strstr(line, "not found")) {
                snprintf(g_game_state.error_message, sizeof(g_game_state.error_message),
                         "El jugador %s no existe o no esta en partida", g_game_state.observe_target);
                TraceLog(LOG_WARNING, "[OBSERVER] %s", g_game_state.error_message);
            } else if (strstr(line, "full") || strstr(line, "403")) {
                snprintf(g_game_state.error_message, sizeof(g_game_state.error_message),
                         "La sala del jugador %s ya tiene el maximo de observadores (2)", g_game_state.observe_target);
                TraceLog(LOG_WARNING, "[OBSERVER] %s", g_game_state.error_message);
            } else {
                snprintf(g_game_state.error_message, sizeof(g_game_state.error_message),
                         "Error al conectar: %s", line);
            }
            
            g_game_state.error_message_until = GetTime() + 5.0;  // Mostrar por 5 segundos
            g_connected=false;
            net_disconnect();
            // NO volver al menú, quedarse en pantalla de selección para mostrar el error
            g_game_state.waiting_observer_selection = true;
            return; 
        }
    }
    if      (strncmp(line, "STATE ", 6)==0) parse_state_line(line);
    else if (strncmp(line, "PONG",  4)==0) TraceLog(LOG_DEBUG, "PONG received");
    else if (strncmp(line, "ACK",   3)==0) TraceLog(LOG_DEBUG, "ACK: %s", line);
    else if (strncmp(line, "SCORE", 5)==0) TraceLog(LOG_DEBUG, "Score update: %s", line);
    else if (strncmp(line, "LEVEL", 5)==0) TraceLog(LOG_INFO, "Level change: %s", line);
    else if (strncmp(line, "DEAD", 4)==0) {
        TraceLog(LOG_INFO, "Player died: %s", line);
        // Resetear la llave cuando el jugador muere
        g_level.hasKey = false;
        g_respawn_flash_until = GetTime() + 0.35;
    }
    else if (strncmp(line, "PLAYER_DISCONNECTED", 19)==0) {
        TraceLog(LOG_WARNING, "Observed player disconnected");
        g_game_state.player_disconnected = true;
        g_connected = false;
    }
}
static void on_net_disconnect(void) { 
    TraceLog(LOG_WARNING, "Disconnected from server"); 
    g_connected=false;
    
    // Si estabas observando, marcar que el jugador se desconectó
    if (g_game_state.mode == MODE_OBSERVER) {
        g_game_state.player_disconnected = true;
        TraceLog(LOG_WARNING, "Player %s disconnected while observing", g_game_state.observe_target);
    } else {
        g_game_state.mode = MODE_MENU;  // Volver al menú al desconectarse
    }
}

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

    // Cargar 3 tipos de frutas
    const char* fruit_bananas_paths[] = { "assets/fruit_bananas.png", "output/assets/fruit_bananas.png", "../output/assets/fruit_bananas.png" };
    ok = false; for (int i=0;i<(int)(sizeof(fruit_bananas_paths)/sizeof(fruit_bananas_paths[0]));++i)
        if (load_texture_cropped(&g_fruit_sprites[0], fruit_bananas_paths[i], "Bananas")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(16,16,BLANK); ImageDrawCircle(&img,8,8,7,YELLOW);
               g_fruit_sprites[0] = LoadTextureFromImage(img); UnloadImage(img); }
    
    const char* fruit_oranges_paths[] = { "assets/fruit_oranges.png", "output/assets/fruit_oranges.png", "../output/assets/fruit_oranges.png" };
    ok = false; for (int i=0;i<(int)(sizeof(fruit_oranges_paths)/sizeof(fruit_oranges_paths[0]));++i)
        if (load_texture_cropped(&g_fruit_sprites[1], fruit_oranges_paths[i], "Oranges")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(16,16,BLANK); ImageDrawCircle(&img,8,8,7,ORANGE);
               g_fruit_sprites[1] = LoadTextureFromImage(img); UnloadImage(img); }
    
    const char* fruit_strawberry_paths[] = { "assets/fruit_strawberry.png", "output/assets/fruit_strawberry.png", "../output/assets/fruit_strawberry.png" };
    ok = false; for (int i=0;i<(int)(sizeof(fruit_strawberry_paths)/sizeof(fruit_strawberry_paths[0]));++i)
        if (load_texture_cropped(&g_fruit_sprites[2], fruit_strawberry_paths[i], "Strawberry")) { ok=true; break; }
    if (!ok) { Image img = GenImageColor(16,16,BLANK); ImageDrawCircle(&img,8,8,7,RED);
               g_fruit_sprites[2] = LoadTextureFromImage(img); UnloadImage(img); }
    
    if (!ok) TraceLog(LOG_WARNING, "Algunos sprites de fruta no encontrados, usando procedural");

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
    for (int i = 0; i < 3; i++) {
        if (g_fruit_sprites[i].id) UnloadTexture(g_fruit_sprites[i]);
    }
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
        snprintf(txt, sizeof(txt), "%+d", g_popups[i].points);  // %+d muestra + o - según el signo
        Color textColor = (g_popups[i].points >= 0) ? (Color){255, 255, 0, alpha} : (Color){255, 50, 50, alpha};
        DrawText(txt,
                 (int)(g_popups[i].pos.x - MeasureText(txt, 20)/2),
                 (int)(g_popups[i].pos.y - 40 + yOffset),
                 20,
                 textColor);
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

    // Mensaje de desconexión
    if (!g_connected) { 
        DrawRectangle(0,570,800,30,(Color){150,0,0,200}); 
        DrawText("DESCONECTADO", 300, 575, 20, WHITE); 
    }
    
    // Mensaje especial cuando el jugador observado se desconecta
    if (g_game_state.player_disconnected && g_game_state.mode == MODE_OBSERVER) {
        DrawRectangle(0, 0, 800, 600, (Color){0, 0, 0, 180});
        const char* msg1 = "Player disconnected";
        const char* msg2 = "Press ESC to return to menu";
        int w1 = MeasureText(msg1, 40);
        int w2 = MeasureText(msg2, 20);
        DrawText(msg1, (800 - w1) / 2, 250, 40, RED);
        DrawText(msg2, (800 - w2) / 2, 320, 20, YELLOW);
    }
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

// ============ MENÚ PRINCIPAL ============
typedef struct {
    Rectangle bounds;
    const char* text;
    Color color;
    Color hoverColor;
    bool hovered;
} MenuButton;

static void draw_menu_background(void) {
    // Fondo degradado
    DrawRectangleGradientV(0, 0, 800, 600, (Color){20, 20, 40, 255}, (Color){10, 10, 20, 255});
    
    // Título del juego
    const char* title = "DONCEY KONG JR";
    int titleSize = 60;
    int titleWidth = MeasureText(title, titleSize);
    
    // Sombra del título
    DrawText(title, (800 - titleWidth) / 2 + 3, 80 + 3, titleSize, (Color){0, 0, 0, 180});
    
    // Título con efecto de color
    DrawText(title, (800 - titleWidth) / 2, 80, titleSize, (Color){255, 215, 0, 255});
    
    // Subtítulo
    const char* subtitle = "Deluxe Edition";
    int subtitleSize = 20;
    int subtitleWidth = MeasureText(subtitle, subtitleSize);
    DrawText(subtitle, (800 - subtitleWidth) / 2, 160, subtitleSize, (Color){200, 200, 200, 255});
}

static bool draw_button(MenuButton* btn) {
    Vector2 mousePos = GetMousePosition();
    btn->hovered = CheckCollisionPointRec(mousePos, btn->bounds);
    
    Color currentColor = btn->hovered ? btn->hoverColor : btn->color;
    
    // Dibujar botón con efecto hover
    DrawRectangleRounded(btn->bounds, 0.3f, 10, currentColor);
    
    // Borde
    Color borderColor = btn->hovered ? (Color){255, 255, 255, 255} : (Color){150, 150, 150, 255};
    DrawRectangleRoundedLines(btn->bounds, 0.3f, 10, borderColor);
    
    // Texto centrado
    int textWidth = MeasureText(btn->text, 30);
    int textX = (int)(btn->bounds.x + (btn->bounds.width - textWidth) / 2);
    int textY = (int)(btn->bounds.y + (btn->bounds.height - 30) / 2);
    
    DrawText(btn->text, textX, textY, 30, WHITE);
    
    // Retornar true si fue clickeado
    return btn->hovered && IsMouseButtonPressed(MOUSE_LEFT_BUTTON);
}

static GameMode draw_and_handle_menu(void) {
    draw_menu_background();
    
    // Crear botones
    MenuButton playBtn = {
        .bounds = (Rectangle){250, 250, 300, 60},
        .text = "PLAY",
        .color = (Color){34, 139, 34, 255},
        .hoverColor = (Color){50, 205, 50, 255}
    };
    
    MenuButton observerBtn = {
        .bounds = (Rectangle){250, 330, 300, 60},
        .text = "OBSERVER",
        .color = (Color){30, 144, 255, 255},
        .hoverColor = (Color){65, 105, 225, 255}
    };
    
    MenuButton exitBtn = {
        .bounds = (Rectangle){250, 410, 300, 60},
        .text = "EXIT",
        .color = (Color){178, 34, 34, 255},
        .hoverColor = (Color){220, 20, 60, 255}
    };
    
    // Dibujar y manejar clicks
    if (draw_button(&playBtn)) {
        TraceLog(LOG_INFO, "Play button clicked");
        return MODE_PLAYER;
    }
    
    if (draw_button(&observerBtn)) {
        TraceLog(LOG_INFO, "Observer button clicked");
        return MODE_OBSERVER;
    }
    
    if (draw_button(&exitBtn)) {
        TraceLog(LOG_INFO, "Exit button clicked");
        return MODE_MENU;  // Se manejará en el main
    }
    
    // Mostrar mensaje de error si existe
    if (g_game_state.error_message[0] != '\0' && GetTime() < g_game_state.error_message_until) {
        int errSize = 18;
        int errWidth = MeasureText(g_game_state.error_message, errSize);
        DrawRectangle((800 - errWidth) / 2 - 10, 200, errWidth + 20, 35, (Color){40, 0, 0, 200});
        DrawText(g_game_state.error_message, (800 - errWidth) / 2, 208, errSize, (Color){255, 80, 80, 255});
    }
    
    // Instrucciones en la parte inferior
    const char* instructions = "Use mouse to select an option";
    int instrWidth = MeasureText(instructions, 16);
    DrawText(instructions, (800 - instrWidth) / 2, 520, 16, (Color){150, 150, 150, 255});
    
    return MODE_MENU;
}

// ============ PANTALLA DE SELECCIÓN DE JUGADOR A OBSERVAR ============
typedef enum {
    OBSERVER_NONE = 0,
    OBSERVER_P1,
    OBSERVER_P2,
    OBSERVER_BACK
} ObserverSelection;

static ObserverSelection draw_observer_selection(void) {
    draw_menu_background();
    
    // Título
    const char* title = "Select Player to Observe";
    int titleSize = 30;
    int titleWidth = MeasureText(title, titleSize);
    DrawText(title, (800 - titleWidth) / 2, 180, titleSize, WHITE);
    
    // Información adicional
    const char* info = "Choose which player's game you want to watch";
    int infoSize = 16;
    int infoWidth = MeasureText(info, infoSize);
    DrawText(info, (800 - infoWidth) / 2, 230, infoSize, LIGHTGRAY);
    
    // Mostrar mensaje de error si existe
    if (g_game_state.error_message[0] != '\0' && GetTime() < g_game_state.error_message_until) {
        int errSize = 18;
        int errWidth = MeasureText(g_game_state.error_message, errSize);
        DrawRectangle((800 - errWidth) / 2 - 10, 250, errWidth + 20, 35, (Color){40, 0, 0, 200});
        DrawText(g_game_state.error_message, (800 - errWidth) / 2, 258, errSize, (Color){255, 80, 80, 255});
    }
    
    // Botones para seleccionar jugador
    MenuButton p1Btn = {
        .bounds = (Rectangle){200, 280, 180, 60},
        .text = "Player 1",
        .color = (Color){30, 144, 255, 255},
        .hoverColor = (Color){65, 105, 225, 255}
    };
    
    MenuButton p2Btn = {
        .bounds = (Rectangle){420, 280, 180, 60},
        .text = "Player 2",
        .color = (Color){30, 144, 255, 255},
        .hoverColor = (Color){65, 105, 225, 255}
    };
    
    MenuButton backBtn = {
        .bounds = (Rectangle){250, 380, 300, 50},
        .text = "Back to Menu",
        .color = (Color){100, 100, 100, 255},
        .hoverColor = (Color){150, 150, 150, 255}
    };
    
    // Debug: Mostrar posición del mouse
    Vector2 mousePos = GetMousePosition();
    bool mousePressed = IsMouseButtonPressed(MOUSE_LEFT_BUTTON);
    
    if (draw_button(&p1Btn)) {
        TraceLog(LOG_INFO, "P1 button clicked at (%.0f, %.0f)", mousePos.x, mousePos.y);
        return OBSERVER_P1;
    }
    
    if (draw_button(&p2Btn)) {
        TraceLog(LOG_INFO, "P2 button clicked at (%.0f, %.0f)", mousePos.x, mousePos.y);
        return OBSERVER_P2;
    }
    
    if (draw_button(&backBtn)) {
        TraceLog(LOG_INFO, "Back button clicked at (%.0f, %.0f)", mousePos.x, mousePos.y);
        return OBSERVER_BACK;
    }
    
    // Debug log si se presiona el mouse
    if (mousePressed) {
        TraceLog(LOG_DEBUG, "Mouse clicked at (%.0f, %.0f) but no button detected", mousePos.x, mousePos.y);
    }
    
    // Instrucciones
    const char* instructions = "Max 2 observers per player allowed";
    int instrWidth = MeasureText(instructions, 14);
    DrawText(instructions, (800 - instrWidth) / 2, 520, 14, YELLOW);
    
    return OBSERVER_NONE;
}

static void draw_world(void) {
    lock_world(); World w = g_world; unlock_world();

    level_draw(&g_level);
    if (g_debug_draw) level_draw_debug(&g_level);

    for (int i=0;i<w.fruitCount;i++) if (!w.fruits[i].collected) {
        // Seleccionar sprite según el tipo (0=bananas, 1=oranges, 2=strawberry)
        int type = w.fruits[i].fruitType;
        if (type < 0 || type > 2) type = 0;  // fallback a bananas
        Texture2D sprite = g_fruit_sprites[type];
        
        // Escala para hacer las frutas más grandes
        float scale = 1.8f;
        float scaled_w = sprite.width * scale;
        float scaled_h = sprite.height * scale;
        
        Rectangle src = {0, 0, (float)sprite.width, (float)sprite.height};
        Rectangle dst = {
            w.fruits[i].pos.x - scaled_w / 2,
            w.fruits[i].pos.y - scaled_h / 2,
            scaled_w,
            scaled_h
        };
        
        DrawTexturePro(sprite, src, dst, (Vector2){0,0}, 0.0f, WHITE);
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
    SetConfigFlags(FLAG_VSYNC_HINT);
    InitWindow(800, 600, "DonCey Kong Jr - Raylib");
    SetTargetFPS(60);
    SetExitKey(0);  // Desactivar ESC como tecla de salida (0 = ninguna tecla)

    SetTraceLogLevel(LOG_DEBUG);  // Cambiar a DEBUG para ver más detalles
    const char* appDir = GetApplicationDirectory();
    ChangeDirectory(appDir);
    TraceLog(LOG_INFO, "CWD now: %s", GetWorkingDirectory());

    load_sprites();
    level_init(&g_level);
    
    // Inicializar red
    if (net_startup() != 0) { 
        TraceLog(LOG_ERROR, "Error: net_startup"); 
        CloseWindow();
        return 1; 
    }

    while (!WindowShouldClose()) {
        // WindowShouldClose() permite cerrar con X en cualquier momento
        // ESC solo cambia al menú, no cierra la ventana
        
        // Manejar menú principal
        if (g_game_state.mode == MODE_MENU && !g_game_state.waiting_observer_selection) {
            BeginDrawing();
            GameMode selected = draw_and_handle_menu();
            
            if (selected == MODE_PLAYER) {
                // Limpiar mensaje de error
                g_game_state.error_message[0] = '\0';
                g_game_state.error_message_until = 0.0;
                
                // Conectar como jugador
                if (net_connect(DKJ_SERVER_HOST, DKJ_SERVER_PORT) == 0) {
                    NetCallbacks cb = { .on_message = on_net_message, .on_disconnect = on_net_disconnect };
                    net_start_receiver(cb);
                    net_send(DKJ_MSG_HELLO_PLAYER);
                    g_game_state.mode = MODE_PLAYER;
                    TraceLog(LOG_INFO, "Connecting as PLAYER...");
                } else {
                    TraceLog(LOG_ERROR, "Could not connect to server");
                }
            } else if (selected == MODE_OBSERVER) {
                g_game_state.error_message[0] = '\0';
                g_game_state.error_message_until = 0.0;
                g_game_state.waiting_observer_selection = true;
            }
            
            // Manejar click en EXIT
            if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON)) {
                Vector2 mousePos = GetMousePosition();
                Rectangle exitBounds = {250, 410, 300, 60};
                if (CheckCollisionPointRec(mousePos, exitBounds)) {
                    break;  // Salir del juego
                }
            }
            
            EndDrawing();
            continue;
        }
        
        // Manejar selección de jugador a observar
        if (g_game_state.waiting_observer_selection) {
            TraceLog(LOG_DEBUG, "In observer selection screen");
            BeginDrawing();
            
            ObserverSelection selection = draw_observer_selection();
            
            TraceLog(LOG_DEBUG, "Selection result: %d", selection);
            
            if (selection == OBSERVER_P1 || selection == OBSERVER_P2) {
                // Determinar el índice del jugador (1 o 2)
                const char* playerIndex = (selection == OBSERVER_P1) ? "1" : "2";
                strcpy(g_game_state.observe_target, playerIndex);
                
                TraceLog(LOG_INFO, "Attempting to connect as observer for Player %s", playerIndex);
                
                // Conectar al servidor
                if (net_connect(DKJ_SERVER_HOST, DKJ_SERVER_PORT) == 0) {
                    NetCallbacks cb = { .on_message = on_net_message, .on_disconnect = on_net_disconnect };
                    net_start_receiver(cb);
                    
                    char msg[128];
                    snprintf(msg, sizeof(msg), "HELLO SPECTATOR %s\n", g_game_state.observe_target);
                    net_send(msg);
                    
                    g_game_state.mode = MODE_OBSERVER;
                    g_game_state.waiting_observer_selection = false;
                    TraceLog(LOG_INFO, "Connected as SPECTATOR for %s", g_game_state.observe_target);
                } else {
                    TraceLog(LOG_ERROR, "Could not connect to server");
                    g_game_state.waiting_observer_selection = false;
                }
            } else if (selection == OBSERVER_BACK) {
                TraceLog(LOG_INFO, "Going back to main menu");
                g_game_state.waiting_observer_selection = false;
                g_game_state.mode = MODE_MENU;
                g_game_state.error_message[0] = '\0';
                g_game_state.error_message_until = 0.0;
            }
            
            EndDrawing();
            continue;
        }
        
        // Input de debug y escape
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

        // ESC vuelve al menú (solo si estás en juego o observando)
        if (IsKeyPressed(KEY_ESCAPE) && (g_game_state.mode == MODE_PLAYER || g_game_state.mode == MODE_OBSERVER)) { 
            if (g_connected) {
                net_send(DKJ_MSG_BYE); 
                net_stop_receiver();
                net_disconnect();
            }
            g_connected = false;
            g_hello_done = false;
            g_game_state.mode = MODE_MENU;
            g_game_state.player_disconnected = false;
            g_won = false;
            continue;
        }

        // Solo procesar input si es jugador (no observador)
        if (g_game_state.mode == MODE_PLAYER && g_connected && g_hello_done && !g_won) {
            process_input();
        }

        // Lógica de juego (solo para jugadores)
        if (g_game_state.mode == MODE_PLAYER) {
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
        }

        BeginDrawing();
        ClearBackground((Color){30,30,30,255});
        
        // Dibujar el mundo
        draw_world();
        
        // Mostrar banner si es observador
        if (g_game_state.mode == MODE_OBSERVER) {
            DrawRectangle(0, 0, 800, 40, (Color){0, 0, 0, 200});
            char banner[128];
            snprintf(banner, sizeof(banner), "OBSERVER MODE - Watching: %s", g_game_state.observe_target);
            int bannerWidth = MeasureText(banner, 24);
            DrawText(banner, (800 - bannerWidth) / 2, 8, 24, YELLOW);
            
            // Instrucciones
            const char* instruction = "Press ESC to return to menu";
            int instrWidth = MeasureText(instruction, 16);
            DrawText(instruction, (800 - instrWidth) / 2, 560, 16, LIGHTGRAY);
        }
        
        EndDrawing();
    }

    unload_sprites();
    net_stop_receiver();
    net_disconnect();
    net_cleanup();
    CloseWindow();
    return 0;
}
