// Cliente Donkey Kong Jr - 100% Raylib (red en módulo separado)
#include "raylib.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <ctype.h>

#include "network.h"   // API de red limpia (sin winsock visible)
#include "constants.h"
#include "types.h"

// ============ DECLARACIONES (level.c) ============
void level_init(Level* lvl);
void level_draw(Level* lvl);
bool level_check_ground(Level* lvl, Vector2 pos, float width, float height);
bool level_can_grab_liana(Level* lvl, Vector2 pos, int* outIndex);
bool level_check_win(Level* lvl, Vector2 playerPos);

// ============ ESTADO GLOBAL ============
static World g_world = {0};
static Level g_level = {0};
static char g_local_id[64] = "";
static bool g_connected = false;
static bool g_hello_done = false;

// Spinlock simple para el snapshot
static volatile bool g_world_lock = false;
static void lock_world(void)   { while (g_world_lock) { /* spin */ } g_world_lock = true; }
static void unlock_world(void) { g_world_lock = false; }

// ============ HELPERS DE MAPEOS (ajusta a tu layout) ============
static inline float liana_to_x(int l /*1..N*/) {
    // Mismo spacing que el servidor (Level.java): x = 100 + 120*(i)
    if (l < 1) l = 1;
    return 100.0f + 120.0f * (float)(l - 1);
}

static inline float height_to_y(int h /*0..K*/) {
    // Base 540 (abajo) y cada nivel 40 px hacia arriba
    return 540.0f - 40.0f * (float)h;
}

// ============ PARSING ============
static float parse_float_loose(const char* s) {
    while (*s && !isdigit((unsigned char)*s) && *s != '-' && *s != '.') s++;
    return (float)atof(s);
}

static void parse_players_block(const char* start, const char* end) {
    // Parsear SOLO mi jugador (por ahora)
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf) - 1;
    memcpy(buf, start, len);
    buf[len] = '\0';

    // Tokenizar por |
    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item) {
        char idbuf[64] = {0};
        const char* id_ptr = strstr(item, "id=");
        if (id_ptr) {
            id_ptr += 3;
            int i = 0;
            while (id_ptr[i] && id_ptr[i] != ',' && i < 63) {
                idbuf[i] = id_ptr[i];
                i++;
            }
            idbuf[i] = '\0';
        }

        if (idbuf[0] && g_local_id[0] && strcmp(idbuf, g_local_id) == 0) {
            PlayerState* p = &g_world.players[0];
            memset(p, 0, sizeof(*p));
            strncpy(p->id, idbuf, sizeof(p->id) - 1);
            p->id[sizeof(p->id) - 1] = '\0';

            const char* x_ptr = strstr(item, "x=");
            const char* y_ptr = strstr(item, "y=");
            const char* liana_ptr = strstr(item, "onLiana=");

            if (x_ptr) p->pos.x = parse_float_loose(x_ptr + 2);
            if (y_ptr) p->pos.y = parse_float_loose(y_ptr + 2);
            if (liana_ptr) p->onLiana = (parse_float_loose(liana_ptr + 8) > 0.5f);

            g_world.playerCount = 1;
            break;
        }

        item = strtok_r(NULL, "|", &saveptr);
    }
}

static void parse_reds_block(const char* start, const char* end) {
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf) - 1;
    memcpy(buf, start, len);
    buf[len] = '\0';

    g_world.redCount = 0;

    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item && g_world.redCount < MAX_REDS) {
        int l = 0, h = 0;
        (void)sscanf(item, " l=%d , h=%d ", &l, &h);

        RedCroc* r = &g_world.reds[g_world.redCount];
        r->pos.x = liana_to_x(l);
        r->pos.y = height_to_y(h);
        r->lianaIndex = (l > 0) ? (l - 1) : 0;
        // Defaults razonables
        r->speed = 60.0f;
        r->minH  = 0.0f;
        r->maxH  = (float)h;
        r->goingUp = false;

        g_world.redCount++;
        item = strtok_r(NULL, "|", &saveptr);
    }
}

static void parse_blues_block(const char* start, const char* end) {
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf) - 1;
    memcpy(buf, start, len);
    buf[len] = '\0';

    g_world.blueCount = 0;

    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item && g_world.blueCount < MAX_BLUES) {
        int l = 0, h = 0;
        (void)sscanf(item, " l=%d , h=%d ", &l, &h);

        BlueCroc* b = &g_world.blues[g_world.blueCount];
        b->pos.x = liana_to_x(l);
        b->pos.y = height_to_y(h);
        b->lianaIndex = (l > 0) ? (l - 1) : 0;
        b->speed = 60.0f;
        b->active = true;

        g_world.blueCount++;
        item = strtok_r(NULL, "|", &saveptr);
    }
}

static void parse_fruits_block(const char* start, const char* end) {
    char buf[1024];
    size_t len = (size_t)(end - start);
    if (len >= sizeof(buf)) len = sizeof(buf) - 1;
    memcpy(buf, start, len);
    buf[len] = '\0';

    g_world.fruitCount = 0;

    char* saveptr = NULL;
    char* item = strtok_r(buf, "|", &saveptr);
    while (item && g_world.fruitCount < MAX_FRUITS) {
        int l = 0, h = 0, pts = 0;
        (void)sscanf(item, " l=%d , h=%d , pts=%d ", &l, &h, &pts);

        Fruit* f = &g_world.fruits[g_world.fruitCount];
        f->pos.x = liana_to_x(l);
        f->pos.y = height_to_y(h);
        f->points = pts;
        f->collected = false;

        g_world.fruitCount++;
        item = strtok_r(NULL, "|", &saveptr);
    }
}

static void parse_state_line(const char* line) {
    lock_world();

    // Reset contadores (no tocamos arrays fuera de rango)
    g_world.playerCount = 0;
    g_world.redCount    = 0;
    g_world.blueCount   = 0;
    g_world.fruitCount  = 0;

    // players=[...]
    const char* pStart = strstr(line, "players=[");
    if (pStart && g_local_id[0]) {
        pStart += 9;
        const char* pEnd = strchr(pStart, ']');
        if (pEnd) parse_players_block(pStart, pEnd);
    }

    // reds=[...]
    const char* rStart = strstr(line, "reds=[");
    if (rStart) {
        rStart += 6;
        const char* rEnd = strchr(rStart, ']');
        if (rEnd) parse_reds_block(rStart, rEnd);
    }

    // blues=[...]
    const char* bStart = strstr(line, "blues=[");
    if (bStart) {
        bStart += 7;
        const char* bEnd = strchr(bStart, ']');
        if (bEnd) parse_blues_block(bStart, bEnd);
    }

    // fruits=[...]
    const char* fStart = strstr(line, "fruits=[");
    if (fStart) {
        fStart += 8;
        const char* fEnd = strchr(fStart, ']');
        if (fEnd) parse_fruits_block(fStart, fEnd);
    }

    unlock_world();
}

// ============ CALLBACKS DE RED ============
static void on_net_message(const char* line) {
    // Primera respuesta: OK con ID
    if (!g_hello_done) {
        if (strncmp(line, "OK ", 3) == 0) {
            const char* p = line + 3;
            size_t L = 0;
            while (p[L] && p[L] != '\r' && p[L] != '\n' && L < sizeof(g_local_id) - 1) L++;
            memcpy(g_local_id, p, L);
            g_local_id[L] = '\0';
            g_hello_done = true;
            g_connected = true;
            TraceLog(LOG_INFO, "Connected as: %s", g_local_id);
            return;
        }
        if (strncmp(line, "ERR", 3) == 0) {
            TraceLog(LOG_ERROR, "Server error: %s", line);
            g_connected = false;
            return;
        }
    }

    // Mensajes normales
    if (strncmp(line, "STATE ", 6) == 0) {
        parse_state_line(line);
    } else if (strncmp(line, "PONG", 4) == 0) {
        TraceLog(LOG_DEBUG, "PONG received");
    } else if (strncmp(line, "ACK", 3) == 0) {
        TraceLog(LOG_DEBUG, "ACK: %s", line);
    }
}

static void on_net_disconnect(void) {
    TraceLog(LOG_WARNING, "Disconnected from server");
    g_connected = false;
}

// ============ INPUT (solo envía comandos al servidor) ============
static void process_input(void) {
    // Enviar solo cuando se presiona la tecla (no mantener presionada)
    if (IsKeyPressed(KEY_LEFT) || IsKeyPressed(KEY_A)) {
        net_send("MOVE LEFT\n");
    }
    if (IsKeyPressed(KEY_RIGHT) || IsKeyPressed(KEY_D)) {
        net_send("MOVE RIGHT\n");
    }
    if (IsKeyPressed(KEY_UP) || IsKeyPressed(KEY_W)) {
        net_send("MOVE UP\n");
    }
    if (IsKeyPressed(KEY_DOWN) || IsKeyPressed(KEY_S)) {
        net_send("MOVE DOWN\n");
    }
    if (IsKeyPressed(KEY_SPACE)) {
        net_send("MOVE JUMP\n");
    }
}

// ============ RENDER ============
static void draw_player(PlayerState* p) {
    Color color = GREEN;

    // Jr (el servidor envía la posición del CENTRO del personaje)
    // Dibujamos cuerpo centrado en x, con pies en y
    Vector2 center = (Vector2){ p->pos.x, p->pos.y + 15.0f }; // offset para centrar

    DrawCircleV(center, 15.0f, color);
    DrawRectangle((int)p->pos.x - 8, (int)p->pos.y + 15, 16, 20, color);

    // Ojos
    int eyeOffset = p->facingRight ? 5 : -5;
    DrawCircle((int)center.x + eyeOffset, (int)center.y - 5, 3, BLACK);

    // Debug: punto en el centro real
    DrawCircle((int)p->pos.x, (int)p->pos.y, 2, RED);
}

static void draw_world(void) {
    lock_world();
    World w = g_world; // copia local para evitar dibujar con lock
    unlock_world();

    // Nivel
    level_draw(&g_level);

    // Frutas
    for (int i = 0; i < w.fruitCount; i++) {
        if (!w.fruits[i].collected) {
            DrawCircleV(w.fruits[i].pos, 8, YELLOW);
            DrawCircleV(w.fruits[i].pos, 6, ORANGE);
        }
    }

    // Cocodrilos azules
    for (int i = 0; i < w.blueCount; i++) {
        if (w.blues[i].active) {
            DrawRectangleV(
                (Vector2){w.blues[i].pos.x - 12, w.blues[i].pos.y - 12},
                (Vector2){24, 24},
                BLUE
            );
        }
    }

    // Cocodrilos rojos (¡sin .active en tu struct!)
    for (int i = 0; i < w.redCount; i++) {
        DrawRectangleV(
            (Vector2){w.reds[i].pos.x - 14, w.reds[i].pos.y - 14},
            (Vector2){28, 28},
            RED
        );
    }

    // Jugador local (si está)
    for (int i = 0; i < w.playerCount; i++) {
        draw_player(&w.players[i]);
    }

    // HUD
    DrawRectangle(0, 0, 800, 50, (Color){0, 0, 0, 180});
    DrawText("WASD/Flechas: Mover | Espacio: Saltar | W/Up: Trepar | ESC: Salir", 10, 10, 16, WHITE);

    if (g_local_id[0]) {
        char hud[128];
        snprintf(hud, sizeof(hud), "ID: %.20s", g_local_id);
        DrawText(hud, 10, 30, 16, LIME);
    }

    if (!g_connected) {
        DrawRectangle(0, 570, 800, 30, (Color){150, 0, 0, 200});
        DrawText("DESCONECTADO", 300, 575, 20, WHITE);
    }
}

// ============ MAIN ============
int main(void) {
    // Red
    if (net_startup() != 0) {
        printf("Error: net_startup\n");
        return 1;
    }

    if (net_connect(DKJ_SERVER_HOST, DKJ_SERVER_PORT) != 0) {
        printf("Error: No se pudo conectar a %s:%d\n", DKJ_SERVER_HOST, DKJ_SERVER_PORT);
        net_cleanup();
        return 1;
    }

    // Iniciar receptor
    NetCallbacks cb = {
        .on_message = on_net_message,
        .on_disconnect = on_net_disconnect
    };
    net_start_receiver(cb);

    // Enviar HELLO
    net_send(DKJ_MSG_HELLO_PLAYER);

    // Raylib
    SetConfigFlags(FLAG_VSYNC_HINT);
    InitWindow(800, 600, "Donkey Kong Jr - Raylib");
    SetTargetFPS(60);

    level_init(&g_level);

    bool won = false;

    // Loop principal
    while (!WindowShouldClose()) {
        if (IsKeyPressed(KEY_ESCAPE)) {
            net_send(DKJ_MSG_BYE);
            break;
        }

        // Procesar input (enviar al servidor)
        if (g_connected && g_hello_done && !won) {
            process_input();
        }

        // Verificar victoria (cliente solo verifica posición)
        lock_world();
        bool havePlayer = (g_world.playerCount > 0);
        Vector2 myPos = havePlayer ? g_world.players[0].pos : (Vector2){0,0};
        unlock_world();

        if (havePlayer && !won) {
            if (level_check_win(&g_level, myPos)) {
                won = true;
                TraceLog(LOG_INFO, "Victoria!");
            }
        }

        // Render
        BeginDrawing();
        ClearBackground((Color){ 30, 30, 30, 255 });
        draw_world();

        if (won) {
            DrawRectangle(0, 0, 800, 600, (Color){0, 0, 0, 200});
            DrawText("VICTORIA!", 250, 250, 60, YELLOW);
            DrawText("Rescataste a Donkey Kong", 220, 320, 30, WHITE);
            DrawText("ESC para salir", 290, 370, 20, LIGHTGRAY);
        }

        EndDrawing();
    }

    // Cleanup
    net_stop_receiver();
    net_disconnect();
    net_cleanup();
    CloseWindow();

    return 0;
}
