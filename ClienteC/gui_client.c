#define _WIN32_WINNT 0x0601
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include "constants.h"

#pragma comment(lib, "ws2_32.lib")

// ---------------- Estructuras de estado ----------------
typedef struct { int l, h; } LH;

typedef struct {
    char id[64];
    LH   pos;
} PlayerDraw;

typedef struct {
    // snapshot que dibuja el hilo de GUI (protegido por CRITICAL_SECTION)
    PlayerDraw players[8]; int nPlayers;
    LH reds[32];  int nReds;
    LH blues[32]; int nBlues;
    struct { LH pos; int pts; } fruits[32]; int nFruits;
} World;

// estado global (simple)
static World g_world;
static CRITICAL_SECTION g_cs;
static SOCKET g_sock = INVALID_SOCKET;
static HWND g_hwnd = NULL;

// ---------------- Util & Parse helpers -----------------
static int parse_int_loose(const char* s) {
    while (*s && !isdigit((unsigned char)*s) && *s!='-') s++;
    return atoi(s);
}
static const char* find_section(const char* line, const char* key, const char** outBegin, const char** outEnd){
    const char* k = strstr(line, key);
    if (!k) return NULL;
    const char* lb = strchr(k, '[');
    if (!lb) return NULL;
    const char* rb = strchr(lb, ']');
    if (!rb) return NULL;
    *outBegin = lb + 1; *outEnd = rb;
    return k;
}
static void clamp_lh(LH* p){
    if (p->l < 1) p->l = 1;
    if (p->l > DKJ_LIANAS) p->l = DKJ_LIANAS;
    if (p->h < 0) p->h = 0;
    if (p->h > DKJ_HEIGHT_MAX) p->h = DKJ_HEIGHT_MAX;
}
static void parse_players(World* w, const char* seg, size_t len){
    w->nPlayers = 0;
    char tmp[512]; if (len >= sizeof(tmp)) len = sizeof(tmp)-1;
    memcpy(tmp, seg, len); tmp[len]=0;
    char* ctx = NULL; char* item = strtok_s(tmp, "|", &ctx);
    while (item && w->nPlayers < (int)(sizeof(w->players)/sizeof(w->players[0]))){
        PlayerDraw* pd = &w->players[w->nPlayers];
        memset(pd, 0, sizeof(*pd));
        // id=...,l=N,h=M
        const char* idk = strstr(item, "id=");
        const char* lk  = strstr(item, "l=");
        const char* hk  = strstr(item, "h=");
        if (idk){ idk += 3; // copy until comma or end
            int i=0; while (*idk && *idk!=',' && i< (int)sizeof(pd->id)-1) pd->id[i++]=*idk++;
            pd->id[i]=0;
        } else { strcpy(pd->id, "P"); }
        pd->pos.l = lk ? parse_int_loose(lk+2) : 1;
        if (hk){
            if (strncmp(hk+2, "MAX", 3)==0) pd->pos.h = DKJ_HEIGHT_MAX;
            else pd->pos.h = parse_int_loose(hk+2);
        } else pd->pos.h = 0;
        clamp_lh(&pd->pos);
        w->nPlayers++;
        item = strtok_s(NULL, "|", &ctx);
    }
}
static void parse_lh_list(LH* arr, int* count, int max, const char* seg, size_t len){
    *count = 0;
    char tmp[512]; if (len >= sizeof(tmp)) len = sizeof(tmp)-1;
    memcpy(tmp, seg, len); tmp[len]=0;
    char* ctx=NULL; char* item = strtok_s(tmp, "|", &ctx);
    while (item && *count < max){
        const char* lk = strstr(item, "l=");
        const char* hk = strstr(item, "h=");
        LH v = {1,0};
        v.l = lk ? parse_int_loose(lk+2) : 1;
        if (hk){
            if (strncmp(hk+2, "MAX", 3)==0) v.h = DKJ_HEIGHT_MAX;
            else v.h = parse_int_loose(hk+2);
        }
        clamp_lh(&v);
        arr[(*count)++] = v;
        item = strtok_s(NULL, "|", &ctx);
    }
}
static void parse_fruits(World* w, const char* seg, size_t len){
    w->nFruits=0;
    char tmp[512]; if (len >= sizeof(tmp)) len = sizeof(tmp)-1;
    memcpy(tmp, seg, len); tmp[len]=0;
    char* ctx=NULL; char* item = strtok_s(tmp, "|", &ctx);
    while (item && w->nFruits < (int)(sizeof(w->fruits)/sizeof(w->fruits[0]))){
        const char* lk = strstr(item, "l=");
        const char* hk = strstr(item, "h=");
        const char* pk = strstr(item, "pts=");
        LH v = {1,0}; int pts=50;
        v.l = lk ? parse_int_loose(lk+2) : 1;
        if (hk){
            if (strncmp(hk+2, "MAX", 3)==0) v.h = DKJ_HEIGHT_MAX;
            else v.h = parse_int_loose(hk+2);
        }
        if (pk) pts = parse_int_loose(pk+4);
        clamp_lh(&v);
        w->fruits[w->nFruits].pos = v;
        w->fruits[w->nFruits].pts = pts;
        w->nFruits++;
        item = strtok_s(NULL, "|", &ctx);
    }
}
static void apply_state_line(const char* line){
    // Esperamos: STATE players=[...] reds=[...] blues=[...] fruits=[...]
    const char *b, *e;
    EnterCriticalSection(&g_cs);
    if (find_section(line, "players=", &b, &e)) parse_players(&g_world, b, (size_t)(e-b)); else g_world.nPlayers=0;
    if (find_section(line, "reds=",    &b, &e)) parse_lh_list(g_world.reds,  &g_world.nReds,  (int)(sizeof(g_world.reds)/sizeof(g_world.reds[0])), b, (size_t)(e-b)); else g_world.nReds=0;
    if (find_section(line, "blues=",   &b, &e)) parse_lh_list(g_world.blues, &g_world.nBlues, (int)(sizeof(g_world.blues)/sizeof(g_world.blues[0])), b, (size_t)(e-b)); else g_world.nBlues=0;
    if (find_section(line, "fruits=",  &b, &e)) parse_fruits(&g_world, b, (size_t)(e-b)); else g_world.nFruits=0;
    LeaveCriticalSection(&g_cs);
    InvalidateRect(g_hwnd, NULL, FALSE);
}

// ---------------- Net helpers -------------------------
static int net_startup(void){ WSADATA w; return WSAStartup(MAKEWORD(2,2), &w)==0?0:-1; }
static void net_cleanup(void){ WSACleanup(); }
static int connect_tcp(SOCKET* s, const char* host, int port){
    struct addrinfo hints, *res=0;
    char pbuf[16]; snprintf(pbuf,sizeof(pbuf), "%d", port);
    memset(&hints,0,sizeof(hints)); hints.ai_family=AF_INET; hints.ai_socktype=SOCK_STREAM;
    if (getaddrinfo(host,pbuf,&hints,&res)!=0) return -1;
    SOCKET sk = socket(res->ai_family,res->ai_socktype,res->ai_protocol);
    if (sk==INVALID_SOCKET){ freeaddrinfo(res); return -2; }
    if (connect(sk,res->ai_addr,(int)res->ai_addrlen)!=0){ closesocket(sk); freeaddrinfo(res); return -3; }
    freeaddrinfo(res); *s = sk; return 0;
}
static int send_line(SOCKET s, const char* line){ return send(s, line, (int)strlen(line), 0); }
static int recv_line(SOCKET s, char* out, int maxlen){
    int pos=0;
    while (pos+1 < maxlen){
        char ch; int n = recv(s, &ch, 1, 0);
        if (n<=0) return n;
        out[pos++] = ch;
        if (ch=='\n') break;
    }
    out[pos]=0; return pos;
}

// ---------------- Render (GDI) -------------------------
static void lh_to_xy(const LH lh, int* x, int* y, RECT rc){
    int w = rc.right - rc.left;
    int h = rc.bottom - rc.top;
    int usableW = w - 2*MARGIN_X;
    int usableH = h - 2*MARGIN_Y;
    if (usableW<10) usableW=10; if (usableH<10) usableH=10;

    double dx = (DKJ_LIANAS>1) ? (double)usableW/(double)(DKJ_LIANAS-1) : (double)usableW;
    *x = MARGIN_X + (int)((lh.l-1)*dx);

    double step = (DKJ_HEIGHT_MAX>0) ? (double)usableH/(double)DKJ_HEIGHT_MAX : (double)usableH;
    *y = h - MARGIN_Y - (int)(lh.h * step);
}
static void draw_lianas(HDC hdc, RECT rc){
    HPEN pen = CreatePen(PS_SOLID, 2, RGB(160,120,50));
    HPEN old = SelectObject(hdc, pen);
    for (int l=1; l<=DKJ_LIANAS; ++l){
        LH p0 = {l, 0}, p1 = {l, DKJ_HEIGHT_MAX};
        int x0,y0,x1,y1; lh_to_xy(p0,&x0,&y0,rc); lh_to_xy(p1,&x1,&y1,rc);
        MoveToEx(hdc, x0, y1, NULL); LineTo(hdc, x0, y0);
    }
    SelectObject(hdc, old); DeleteObject(pen);
}
static void fill_disc(HDC hdc, int cx, int cy, int r, COLORREF c){
    HBRUSH b = CreateSolidBrush(c); HBRUSH oldb = SelectObject(hdc,b);
    HPEN   p = CreatePen(PS_NULL, 0, c); HPEN oldp = SelectObject(hdc,p);
    Ellipse(hdc, cx-r, cy-r, cx+r, cy+r);
    SelectObject(hdc, oldb); DeleteObject(b);
    SelectObject(hdc, oldp); DeleteObject(p);
}
static void fill_rect(HDC hdc, int cx, int cy, int a, int b, COLORREF c){
    HBRUSH br = CreateSolidBrush(c); HBRUSH old = SelectObject(hdc, br);
    RECT r = { cx-a/2, cy-b/2, cx+a/2, cy-b/2+b };
    FillRect(hdc, &r, br);
    SelectObject(hdc, old); DeleteObject(br);
}
static void paint_scene(HDC hdc, RECT rc){
    EnterCriticalSection(&g_cs);
    World W = g_world; // copia rápida
    LeaveCriticalSection(&g_cs);

    // fondo
    HBRUSH bg = CreateSolidBrush(RGB(15,15,22));
    FillRect(hdc, &rc, bg); DeleteObject(bg);

    // lianas
    draw_lianas(hdc, rc);

    // frutas
    for (int i=0;i<W.nFruits;i++){
        int x,y; lh_to_xy(W.fruits[i].pos, &x,&y, rc);
        fill_disc(hdc, x, y, FRUIT_SIZE/2, RGB(255,196,0));
    }
    // cocodrilos azules (caen)
    for (int i=0;i<W.nBlues;i++){
        int x,y; lh_to_xy(W.blues[i], &x,&y, rc);
        fill_rect(hdc, x, y, BLUE_SIZE, BLUE_SIZE, RGB(0,160,255));
    }
    // cocodrilos rojos (oscilan)
    for (int i=0;i<W.nReds;i++){
        int x,y; lh_to_xy(W.reds[i], &x,&y, rc);
        fill_rect(hdc, x, y, RED_SIZE, RED_SIZE, RGB(255,64,64));
    }
    // jugadores (Jr)
    for (int i=0;i<W.nPlayers;i++){
        int x,y; lh_to_xy(W.players[i].pos, &x,&y, rc);
        fill_disc(hdc, x, y, JR_RADIUS, RGB(60,255,120));
    }

    // HUD simple (texto)
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, RGB(220,220,220));
    TextOutA(hdc, 10, 10, "Flechas: MOVE, Espacio: JUMP, Q/Esc: Salir", 42);
}

// ---------------- Hilo de red --------------------------
static DWORD WINAPI NetThread(LPVOID lp) {
    (void)lp; // evitar warning
    // HELLO PLAYER y luego loop de recepción
    send_line(g_sock, DKJ_MSG_HELLO_PLAYER);

    char line[DKJ_MAX_LINE];
    for (;;) {
        int n = recv_line(g_sock, line, sizeof(line));
        if (n <= 0) break;
        if (strncmp(line, "STATE ", 6) == 0) {
            apply_state_line(line);
        } else {
            // otros (ACK, OK, PONG, ERR, LEVEL...)
            InvalidateRect(g_hwnd, NULL, FALSE);
        }
    }
    PostMessage(g_hwnd, WM_CLOSE, 0, 0);
    return 0;
}

// ---------------- Win32: ventana -----------------------
static LRESULT CALLBACK WndProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_CREATE:
        g_hwnd = h;
        SetTimer(h, 1, 1000 / 60, NULL); // 60 FPS
        return 0;
    case WM_TIMER:
        InvalidateRect(h, NULL, FALSE);
        return 0;
    case WM_KEYDOWN:
        if (w == VK_ESCAPE || w == 'Q' || w == 'q') {
            send_line(g_sock, DKJ_MSG_BYE);
            DestroyWindow(h);
        } else if (w == VK_LEFT)  send_line(g_sock, "MOVE LEFT\n");
        else if (w == VK_RIGHT)   send_line(g_sock, "MOVE RIGHT\n");
        else if (w == VK_UP)      send_line(g_sock, "MOVE UP\n");
        else if (w == VK_DOWN)    send_line(g_sock, "MOVE DOWN\n");
        else if (w == VK_SPACE)   send_line(g_sock, "MOVE JUMP\n");
        return 0;
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(h, &ps);
        paint_scene(hdc, ps.rcPaint);
        EndPaint(h, &ps);
        return 0;
    }
    case WM_DESTROY:
        KillTimer(h, 1);
        if (g_sock != INVALID_SOCKET) {
            closesocket(g_sock);
            g_sock = INVALID_SOCKET;
        }
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(h, m, w, l);
}

// ---------------- Punto de entrada ---------------------
int APIENTRY WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nShowCmd) {
    (void)hPrevInstance;
    (void)lpCmdLine;

    InitializeCriticalSection(&g_cs);
    memset(&g_world, 0, sizeof(g_world));

    if (net_startup() != 0) {
        MessageBoxA(NULL, "WSAStartup failed", "Error", MB_ICONERROR);
        return 1;
    }
    if (connect_tcp(&g_sock, DKJ_SERVER_HOST, DKJ_SERVER_PORT) != 0) {
        MessageBoxA(NULL, "No se pudo conectar al servidor", "Error", MB_ICONERROR);
        net_cleanup();
        return 1;
    }

    // Ventana
    const wchar_t* CLASS = L"DKJ_GUI";
    WNDCLASSW wc = { 0 };
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszClassName = CLASS;
    RegisterClassW(&wc);

    HWND hwnd = CreateWindowExW(
        0, CLASS, L"DK Jr (Cliente C GUI)",
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT,
        WIN_W, WIN_H,
        NULL, NULL, hInstance, NULL
    );

    ShowWindow(hwnd, nShowCmd);
    UpdateWindow(hwnd);

    // Hilo de red
    HANDLE hNet = CreateThread(NULL, 0, NetThread, NULL, 0, NULL);
    (void)hNet;

    // Loop de mensajes
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    net_cleanup();
    DeleteCriticalSection(&g_cs);
    return 0;
}