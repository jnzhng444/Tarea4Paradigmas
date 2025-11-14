final class PlayerPhysics {
    // ===== Campos originales =====
    Float x, y;        // posición en píxeles (x,y es el CENTRO del jugador)
    Float vx, vy;      // velocidad
    Boolean onGround;
    Boolean onLiana;
    Integer lianaIndex;

    // ===== Nuevos campos =====
    Integer score = Integer.valueOf(0);            // puntaje acumulado
    Boolean respawned = Boolean.FALSE; // se marca true al morir; se limpia tras enviar STATE
    Integer lives = Integer.valueOf(0);            // vidas adicionales ganadas
    Integer difficultyLevel = Integer.valueOf(1);  // nivel de dificultad (aumenta al ganar)

    PlayerPhysics() {
        x = Float.valueOf(150.0f);       // centro horizontal
        y = Float.valueOf(490.0f);       // arriba del piso (520 - 30 altura de jugador)
        vx = Float.valueOf(0);
        vy = Float.valueOf(0);
        onGround = Boolean.TRUE;
        onLiana = Boolean.FALSE;
        lianaIndex = Integer.valueOf(-1);
    }

    // ===== Helpers nuevos =====
    void addScore(Integer pts) {
        score += pts;
    }

    void markRespawned() {
        respawned = Boolean.TRUE;
    }

    void clearRespawned() {
        respawned = Boolean.FALSE;
    }
    
    void resetScore() {
        score = Integer.valueOf(0);
    }
    
    void addLife() {
        lives += 1;
    }
    
    void increaseDifficulty() {
        difficultyLevel += 1;
    }
}