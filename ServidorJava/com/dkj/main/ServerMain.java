package com.dkj.main;

import com.dkj.server.LineServer;

import java.util.UUID;

/**
 * Clase principal del servidor de Donkey Kong Jr multijugador.
 * 
 * Esta clase contiene el punto de entrada del programa servidor.
 * Inicializa el servidor TCP en el puerto configurado y genera un
 * identificador unico para la instancia del servidor.
 * 
 * Arquitectura: El servidor utiliza un modelo de comunicacion basado en lineas
 * de texto sobre TCP, donde cada cliente se comunica mediante comandos de protocolo.
 */
public class ServerMain {
    /**
     * Punto de entrada principal del servidor.
     * 
     * Inicializa y arranca el servidor TCP en el puerto 5555 con un identificador
     * unico generado aleatoriamente. El servidor quedara en escucha esperando
     * conexiones de clientes.
     * 
     * @param args Argumentos de linea de comandos (no utilizados actualmente)
     */
    public static void main(String[] args) {
        Integer port = Integer.valueOf(5555);
        LineServer server = new LineServer(port, UUID.randomUUID().toString());
        server.start();
    }
}