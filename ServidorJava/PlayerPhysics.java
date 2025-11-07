package ServidorJava;

final class PlayerPhysics {
    float x, y;        // posición en píxeles (x,y es el CENTRO del jugador)
    float vx, vy;      // velocidad
    boolean onGround;
    boolean onLiana;
    int lianaIndex;

    PlayerPhysics() {
        x = 150.0f;       // centro horizontal
        y = 490.0f;       // arriba del piso (520 - 30 altura de jugador)
        vx = 0;
        vy = 0;
        onGround = true;
        onLiana = false;
        lianaIndex = -1;
    }
}
