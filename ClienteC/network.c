/**
 * @file network.c
 * @brief Implementacion del modulo de red TCP del cliente
 * 
 * Proporciona funciones para comunicacion TCP con el servidor de juego
 * usando WinSock2. Maneja conexion, envio sincrono y recepcion asincrona
 * mediante un hilo dedicado que notifica eventos via callbacks.
 * 
 * Arquitectura:
 * - Conexion bloqueante: net_connect() espera hasta conectar o fallar
 * - Envio sincrono: net_send() envia y retorna inmediatamente
 * - Recepcion asincrona: Hilo separado (recv_thread) lee continuamente
 * - Callbacks: Ejecutados en contexto del hilo receptor
 * 
 * Thread safety:
 * - net_send() es thread-safe (llamadas send() de WinSock son atomicas)
 * - Callbacks se ejecutan serializados en un solo hilo
 * - g_sock compartido pero solo modificado en hilo principal
 */

// network.c - Módulo de red SIN Raylib (evita conflictos)
#define _WIN32_WINNT 0x0601
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include "network.h"

#pragma comment(lib, "ws2_32.lib")

// ============ ESTADO INTERNO ============

/** Socket TCP global, INVALID_SOCKET si no hay conexion */
static SOCKET g_sock = INVALID_SOCKET;

/** Flag que indica si el hilo receptor debe continuar ejecutandose */
static bool g_running = false;

/** Handle del hilo receptor de Windows */
static HANDLE g_thread = NULL;

/** Callbacks registrados para eventos de red */
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

// ============ HILO DE RECEPCION ============

/**
 * Recibe una linea completa del socket (hasta \n).
 * 
 * Lee byte por byte del socket hasta encontrar newline o llenar el buffer.
 * Agrega null terminator al final. Bloqueante hasta recibir linea completa.
 * 
 * @param s Socket desde donde leer
 * @param out Buffer donde almacenar la linea recibida
 * @param maxlen Tamano maximo del buffer (incluyendo null terminator)
 * @return Numero de bytes recibidos (incluyendo \n), 0 si conexion cerrada, <0 si error
 */
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

/**
 * Funcion del hilo receptor de red.
 * 
 * Loop infinito que lee lineas del servidor y ejecuta callbacks cuando:
 * - Se recibe una linea completa: Llama a on_message(line)
 * - El servidor cierra la conexion: Llama a on_disconnect()
 * 
 * El loop termina cuando:
 * - g_running se pone en false (net_stop_receiver())
 * - El socket se cierra o hay error de red
 * - Se detecta desconexion del servidor
 * 
 * @param arg Parametro de thread (no utilizado)
 * @return 0 cuando el thread termina
 */
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