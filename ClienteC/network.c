// network.c - Módulo de red SIN Raylib (evita conflictos)
#define _WIN32_WINNT 0x0601
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include "network.h"

#pragma comment(lib, "ws2_32.lib")

// Estado interno
static SOCKET g_sock = INVALID_SOCKET;
static bool g_running = false;
static HANDLE g_thread = NULL;
static NetCallbacks g_callbacks = {0};

// ============ FUNCIONES PÚBLICAS ============

int net_startup(void) {
    WSADATA wsa;
    return WSAStartup(MAKEWORD(2,2), &wsa) == 0 ? 0 : -1;
}

void net_cleanup(void) {
    WSACleanup();
}

int net_connect(const char* host, int port) {
    struct addrinfo hints = {0}, *res = NULL;
    char pbuf[16];
    snprintf(pbuf, sizeof(pbuf), "%d", port);
    
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    
    if (getaddrinfo(host, pbuf, &hints, &res) != 0) return -1;
    
    SOCKET sk = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (sk == INVALID_SOCKET) {
        freeaddrinfo(res);
        return -2;
    }
    
    if (connect(sk, res->ai_addr, (int)res->ai_addrlen) != 0) {
        closesocket(sk);
        freeaddrinfo(res);
        return -3;
    }
    
    freeaddrinfo(res);
    g_sock = sk;
    return 0;
}

void net_disconnect(void) {
    if (g_sock != INVALID_SOCKET) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
    }
}

int net_send(const char* line) {
    if (g_sock == INVALID_SOCKET) return -1;
    return send(g_sock, line, (int)strlen(line), 0);
}

// ============ HILO DE RECEPCIÓN ============

static int recv_line(SOCKET s, char* out, int maxlen) {
    int pos = 0;
    while (pos + 1 < maxlen) {
        char ch;
        int n = recv(s, &ch, 1, 0);
        if (n <= 0) return n;
        out[pos++] = ch;
        if (ch == '\n') break;
    }
    out[pos] = '\0';
    return pos;
}

static DWORD WINAPI recv_thread(LPVOID arg) {
    (void)arg;
    
    char line[1024];
    
    while (g_running && g_sock != INVALID_SOCKET) {
        int n = recv_line(g_sock, line, sizeof(line));
        if (n <= 0) {
            if (g_callbacks.on_disconnect) {
                g_callbacks.on_disconnect();
            }
            break;
        }
        
        if (g_callbacks.on_message) {
            g_callbacks.on_message(line);
        }
    }
    
    g_running = false;
    return 0;
}

void net_start_receiver(NetCallbacks callbacks) {
    g_callbacks = callbacks;
    g_running = true;
    g_thread = CreateThread(NULL, 0, recv_thread, NULL, 0, NULL);
}

void net_stop_receiver(void) {
    g_running = false;
    if (g_thread) {
        WaitForSingleObject(g_thread, 1000);
        CloseHandle(g_thread);
        g_thread = NULL;
    }
}

bool net_is_connected(void) {
    return g_sock != INVALID_SOCKET && g_running;
}