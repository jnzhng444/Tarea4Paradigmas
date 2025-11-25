/**
 * @file network.h
 * @brief API de red del cliente (wrapper de WinSock2)
 * 
 * Proporciona una interfaz simplificada para comunicacion TCP con el servidor
 * sin exponer detalles de WinSock2. Maneja conexion, desconexion, envio y
 * recepcion asincrona mediante callbacks.
 * 
 * Arquitectura:
 * - Hilo principal: Envia comandos via net_send()
 * - Hilo receptor: Recibe mensajes del servidor y notifica via callbacks
 * - Callbacks: on_message() y on_disconnect() ejecutados en hilo receptor
 * 
 * Ciclo de vida:
 * 1. net_startup() - Inicializa WinSock
 * 2. net_connect() - Conecta al servidor
 * 3. net_start_receiver() - Inicia hilo receptor con callbacks
 * 4. net_send() - Envia comandos (thread-safe)
 * 5. Callbacks procesados en hilo receptor
 * 6. net_stop_receiver() - Detiene hilo receptor
 * 7. net_disconnect() - Cierra conexion
 * 8. net_cleanup() - Libera WinSock
 */

#ifndef NETWORK_H
#define NETWORK_H

#include <stdbool.h>

/**
 * Estructura de callbacks para eventos de red.
 * 
 * Define las funciones que seran invocadas por el hilo receptor cuando
 * ocurren eventos de red. Ambos callbacks se ejecutan en el contexto del
 * hilo receptor, no en el hilo principal.
 * 
 * Campos:
 * - on_message: Llamado cuando se recibe una linea completa del servidor
 * - on_disconnect: Llamado cuando el servidor cierra la conexion
 */
typedef struct {
    void (*on_message)(const char* line);
    void (*on_disconnect)(void);
} NetCallbacks;

// ============ INICIALIZACION ============

/**
 * Inicializa el subsistema de red (WinSock2).
 * 
 * Debe llamarse una vez al inicio del programa antes de usar cualquier
 * otra funcion de red. En Windows, inicializa WSA.
 * 
 * @return 0 si exitoso, -1 si falla la inicializacion
 */
int net_startup(void);

/**
 * Libera recursos del subsistema de red (WinSock2).
 * 
 * Debe llamarse al final del programa para limpiar recursos.
 * En Windows, llama a WSACleanup().
 */
void net_cleanup(void);

// ============ CONEXION ============

/**
 * Establece conexion TCP con el servidor de juego.
 * 
 * Crea un socket TCP y se conecta al host:puerto especificado.
 * Bloquea hasta establecer conexion o fallar.
 * 
 * @param host Direccion IP o hostname del servidor (ej: "127.0.0.1")
 * @param port Numero de puerto TCP del servidor (ej: 5555)
 * @return 0 si exitoso, -1 si falla la conexion
 */
int net_connect(const char* host, int port);

/**
 * Cierra la conexion con el servidor.
 * 
 * Cierra el socket TCP y libera recursos asociados.
 * Es seguro llamarla multiples veces o si no hay conexion activa.
 */
void net_disconnect(void);

// ============ ENVIO ============

/**
 * Envia una linea de texto al servidor.
 * 
 * Envia el texto especificado seguido de newline (\n) al servidor.
 * Esta funcion es thread-safe y puede llamarse desde cualquier hilo.
 * 
 * @param line Texto a enviar (sin incluir \n, se agrega automaticamente)
 * @return 0 si exitoso, -1 si falla el envio o no hay conexion
 */
int net_send(const char* line);

// ============ RECEPCION (hilo separado) ============

/**
 * Inicia el hilo receptor para procesar mensajes del servidor.
 * 
 * Crea un hilo separado que lee continuamente del socket y ejecuta
 * los callbacks cuando recibe mensajes o detecta desconexion.
 * 
 * IMPORTANTE: Los callbacks se ejecutan en el contexto del hilo receptor,
 * no en el hilo principal. Deben ser thread-safe si acceden a datos compartidos.
 * 
 * @param callbacks Estructura con punteros a funciones callback
 */
void net_start_receiver(NetCallbacks callbacks);

/**
 * Detiene el hilo receptor y espera a que termine.
 * 
 * Señaliza al hilo receptor que debe terminar y bloquea hasta que finalice.
 * Es seguro llamarla si el hilo no esta corriendo.
 */
void net_stop_receiver(void);

// ============ ESTADO ============

/**
 * Verifica si hay una conexion activa con el servidor.
 * 
 * @return true si conectado, false si desconectado
 */
bool net_is_connected(void);

#endif