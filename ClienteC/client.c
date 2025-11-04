// Cliente C para Windows (MinGW/MSVC) con WinSock2
#define _WIN32_WINNT 0x0600  // habilita inet_pton/getaddrinfo en ws2tcpip.h

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include "constants.h"

#pragma comment(lib, "ws2_32.lib")  // MSVC; con MinGW igual hay que pasar -lws2_32

typedef struct {
    char   host[64];
    int    port;
    SOCKET sockfd;
} DkjClient;

static int dkj_startup(void) {
    WSADATA wsa;
    int r = WSAStartup(MAKEWORD(2,2), &wsa);
    return (r == 0) ? 0 : -1;
}

static void dkj_cleanup(void) {
    WSACleanup();
}

// Conecta usando getaddrinfo (IPv4) y WinSock
static int dkj_connect(DkjClient* c) {
    char portstr[16];
    struct addrinfo hints, *res = NULL, *p = NULL;
    int rv;

    snprintf(portstr, sizeof(portstr), "%d", c->port);
    memset(&hints, 0, sizeof(hints));
    hints.ai_family   = AF_INET;       // IPv4
    hints.ai_socktype = SOCK_STREAM;   // TCP

    rv = getaddrinfo(c->host, portstr, &hints, &res);
    if (rv != 0) return -1;

    for (p = res; p != NULL; p = p->ai_next) {
        c->sockfd = socket(p->ai_family, p->ai_socktype, p->ai_protocol);
        if (c->sockfd == INVALID_SOCKET) continue;
        if (connect(c->sockfd, p->ai_addr, (int)p->ai_addrlen) == 0) {
            freeaddrinfo(res);
            return 0; // conectado
        }
        closesocket(c->sockfd);
        c->sockfd = INVALID_SOCKET;
    }

    freeaddrinfo(res);
    return -2; // no se pudo conectar
}

static int dkj_send_line(DkjClient* c, const char* line) {
    int len = (int)strlen(line);
    int sent = send(c->sockfd, line, len, 0);
    return sent;
}

// lee hasta '\n' o fin
static int dkj_recv_line(DkjClient* c, char* out, size_t maxlen) {
    size_t pos = 0;
    while (pos + 1 < maxlen) {
        char ch;
        int n = recv(c->sockfd, &ch, 1, 0);
        if (n <= 0) return n; // error o desconexión
        out[pos++] = ch;
        if (ch == '\n') break;
    }
    out[pos] = '\0';
    return (int)pos;
}

int main(void) {
    if (dkj_startup() != 0) {
        fprintf(stderr, "WSAStartup fallo\n");
        return 1;
    }

    DkjClient cli;
    memset(&cli, 0, sizeof(cli));
    strncpy(cli.host, DKJ_SERVER_HOST, sizeof(cli.host)-1);
    cli.port = DKJ_SERVER_PORT;
    cli.sockfd = INVALID_SOCKET;

    if (dkj_connect(&cli) != 0) {
        fprintf(stderr, "No se pudo conectar a %s:%d\n", cli.host, cli.port);
        dkj_cleanup();
        return 1;
    }

    // HELLO
    if (dkj_send_line(&cli, DKJ_MSG_HELLO_PLAYER) <= 0) {
        fprintf(stderr, "Fallo al enviar HELLO\n");
        closesocket(cli.sockfd);
        dkj_cleanup();
        return 1;
    }

    char buf[DKJ_MAX_LINE];
    int n = dkj_recv_line(&cli, buf, sizeof(buf));
    if (n > 0) printf("SERVIDOR> %s", buf);

    // PING/PONG
    dkj_send_line(&cli, DKJ_MSG_PING);
    n = dkj_recv_line(&cli, buf, sizeof(buf));
    if (n > 0) printf("SERVIDOR> %s", buf);

    closesocket(cli.sockfd);
    dkj_cleanup();
    return 0;
}
