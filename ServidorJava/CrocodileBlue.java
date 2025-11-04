package ServidorJava;

import java.util.Objects;

public final class CrocodileBlue implements Entity {
    private final LianaId liana;
    private final Height  height;
    private final Speed   speed;

    public CrocodileBlue(LianaId liana, Height height, Speed speed){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.speed  = Objects.requireNonNull(speed);
    }
    @Override public Position position(){ return new Position(liana, height); }

    public CrocodileBlue step(){
        // Simular “caer” alternando una bandera o anotando estado textual.
        return this;
    }
}
