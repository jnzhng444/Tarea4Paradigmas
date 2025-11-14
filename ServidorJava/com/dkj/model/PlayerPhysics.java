package com.dkj.model;

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
    public void addScore(Integer pts) {
        score += pts;
    }

    public void markRespawned() {
        respawned = Boolean.TRUE;
    }

    public void clearRespawned() {
        respawned = Boolean.FALSE;
    }
    
    public void resetScore() {
        score = Integer.valueOf(0);
    }
    
    public void addLife() {
        lives += 1;
    }
    
    public void loseLife() {
        if (lives > 0) {
            lives -= 1;
        }
    }
    
    public void increaseDifficulty() {
        difficultyLevel += 1;
    }
}