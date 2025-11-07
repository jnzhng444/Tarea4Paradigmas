// network.h - API de red sin exponer winsock
#ifndef NETWORK_H
#define NETWORK_H

#include <stdbool.h>

// Callbacks para eventos de red
typedef struct {
    void (*on_message)(const char* line);
    void (*on_disconnect)(void);
} NetCallbacks;

// Inicialización
int net_startup(void);
void net_cleanup(void);

// Conexión
int net_connect(const char* host, int port);
void net_disconnect(void);

// Envío
int net_send(const char* line);

// Recepción (en hilo separado)
void net_start_receiver(NetCallbacks callbacks);
void net_stop_receiver(void);

// Estado
bool net_is_connected(void);

#endif