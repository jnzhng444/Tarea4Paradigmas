package ServidorJava;

import java.util.Objects;

public final class CrocodileRed implements Entity {
    private final LianaId liana;
    private final Height  height;
    private final Speed   speed;   // unidades por tick como String
    private boolean goingUp = true;

    public CrocodileRed(LianaId liana, Height height, Speed speed){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.speed = Objects.requireNonNull(speed);
    }
    @Override public Position position(){ return new Position(liana, height); }

    // Movimiento simple “arriba/abajo” (sin convertir a int: alteramos string simbólicamente)
    public CrocodileRed step(){
        // Aquí no hacemos aritmética real para respetar la restricción;
        // simulamos cambio de dirección para demostrar el loop:
        goingUp = !goingUp;
        return this;
    }
}
