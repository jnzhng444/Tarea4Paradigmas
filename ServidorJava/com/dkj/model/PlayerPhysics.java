package com.dkj.model;

/**
 * Estado fisico de un jugador en el sistema de juego.
 * 
 * Contiene toda la informacion necesaria para simular la fisica del jugador,
 * incluyendo posicion, velocidad, estado de contacto con superficies, puntaje,
 * vidas y nivel de dificultad.
 * 
 * Sistema de coordenadas:
 * - x, y: Posicion en pixeles donde (x,y) representa el CENTRO del jugador
 * - vx, vy: Velocidad en pixeles por tick
 * 
 * Estados de contacto:
 * - onGround: true si el jugador esta sobre una plataforma
 * - onLiana: true si el jugador esta agarrado a una liana
 * - lianaIndex: Indice de la liana actual (-1 si no esta en ninguna)
 * 
 * Gestion de juego:
 * - score: Puntos acumulados por el jugador
 * - lives: Vidas adicionales obtenidas
 * - difficultyLevel: Nivel de dificultad actual (aumenta al completar niveles)
 * - respawned: Marca temporal que indica que el jugador acaba de reaparecer
 */
public final class PlayerPhysics {
    // ===== Campos originales =====
    public Float x, y;        // posición en píxeles (x,y es el CENTRO del jugador)
    public Float vx, vy;      // velocidad
    public Boolean onGround;
    public Boolean onLiana;
    public Integer lianaIndex;

    // ===== Nuevos campos =====
    public Integer score = Integer.valueOf(0);            // puntaje acumulado
    public Boolean respawned = Boolean.FALSE; // se marca true al morir; se limpia tras enviar STATE
    public Integer lives = Integer.valueOf(0);            // vidas adicionales ganadas
    public Integer difficultyLevel = Integer.valueOf(1);  // nivel de dificultad (aumenta al ganar)

    /**
     * Constructor por defecto que inicializa el jugador en la posicion inicial.
     * 
     * Posicion inicial: x=150.0, y=490.0 (centro-izquierda, sobre el suelo)
     * Velocidad inicial: vx=0, vy=0 (estatico)
     * Estado inicial: en el suelo, no en liana
     */
    public PlayerPhysics() {
        x = Float.valueOf(150.0f);       // centro horizontal
        y = Float.valueOf(490.0f);       // arriba del piso (520 - 30 altura de jugador)
        vx = Float.valueOf(0);
        vy = Float.valueOf(0);
        onGround = Boolean.TRUE;
        onLiana = Boolean.FALSE;
        lianaIndex = Integer.valueOf(-1);
    }

    // ===== Helpers nuevos =====
    
    /**
     * Agrega puntos al puntaje acumulado del jugador.
     * 
     * @param pts Cantidad de puntos a agregar
     */
    public void addScore(Integer pts) {
        score += pts;
    }

    /**
     * Marca que el jugador ha reaparecido tras morir.
     * Esta marca se utiliza para enviar feedback visual al cliente.
     */
    public void markRespawned() {
        respawned = Boolean.TRUE;
    }

    /**
     * Limpia la marca de reaparicion.
     * Debe llamarse despues de enviar el estado al cliente.
     */
    public void clearRespawned() {
        respawned = Boolean.FALSE;
    }
    
    /**
     * Reinicia el puntaje del jugador a cero.
     * Se utiliza al completar un nivel o resetear el juego.
     */
    public void resetScore() {
        score = Integer.valueOf(0);
    }
    
    /**
     * Agrega una vida adicional al jugador.
     * Las vidas se ganan al alcanzar ciertos puntajes.
     */
    public void addLife() {
        lives += 1;
    }
    
    /**
     * Resta una vida al jugador.
     * Si el jugador no tiene vidas adicionales (lives = 0), no hace nada.
     */
    public void loseLife() {
        if (lives > 0) {
            lives -= 1;
        }
    }
    
    /**
     * Incrementa el nivel de dificultad del jugador.
     * Se llama al completar un nivel exitosamente.
     */
    public void increaseDifficulty() {
        difficultyLevel += 1;
    }
}