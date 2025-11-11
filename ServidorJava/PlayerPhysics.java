final class PlayerPhysics {
    // ===== Campos originales =====
    float x, y;        // posición en píxeles (x,y es el CENTRO del jugador)
    float vx, vy;      // velocidad
    boolean onGround;
    boolean onLiana;
    int lianaIndex;

    // ===== Nuevos campos =====
    int score = 0;            // puntaje acumulado
    boolean respawned = false; // se marca true al morir; se limpia tras enviar STATE

    PlayerPhysics() {
        x = 150.0f;       // centro horizontal
        y = 490.0f;       // arriba del piso (520 - 30 altura de jugador)
        vx = 0;
        vy = 0;
        onGround = true;
        onLiana = false;
        lianaIndex = -1;
    }

    // ===== Helpers nuevos =====
    void addScore(int pts) {
        score += pts;
    }

    void markRespawned() {
        respawned = true;
    }

    void clearRespawned() {
        respawned = false;
    }
}
