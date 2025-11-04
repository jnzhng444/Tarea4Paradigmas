package ServidorJava;

import java.util.Objects;

public final class Player {
    private final PlayerId id;
    private Position position; // luego la moveremos con MOVE/colisiones

    public Player(PlayerId id, Position start){
        this.id = Objects.requireNonNull(id);
        this.position = Objects.requireNonNull(start);
    }
    public PlayerId id(){ return id; }
    public Position position(){ return position; }
    public void moveTo(Position p){ this.position = Objects.requireNonNull(p); }
}
