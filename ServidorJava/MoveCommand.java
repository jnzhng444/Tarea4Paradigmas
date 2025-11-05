package ServidorJava;

import java.util.Objects;

public final class MoveCommand {
    private final PlayerId player;
    private final Direction dir;

    public MoveCommand(PlayerId player, Direction dir) {
        this.player = Objects.requireNonNull(player);
        this.dir = Objects.requireNonNull(dir);
    }

    public PlayerId player() { return player; }
    public Direction dir() { return dir; }
}
