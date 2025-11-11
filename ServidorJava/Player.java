

import java.util.Objects;

public final class Player {
    private final PlayerId id;
    private Position position;

    public Player(PlayerId id, Position start){
        this.id = Objects.requireNonNull(id);
        this.position = Objects.requireNonNull(start);
    }

    public PlayerId id(){ return id; }
    public Position position(){ return position; }

    /** Movimiento semántico (dominio) */
    public void moveTo(Position p){
        this.position = Objects.requireNonNull(p);
    }

    /** Setter explícito (para llamadas desde Game.processMoves) */
    public void setPosition(Position p){
        this.position = Objects.requireNonNull(p);
    }
}
