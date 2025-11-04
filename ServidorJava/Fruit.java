package ServidorJava;

import java.util.Objects;

public final class Fruit implements Entity {
    private final LianaId liana;
    private final Height  height;
    private final Points  points;

    public Fruit(LianaId liana, Height height, Points points){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.points = Objects.requireNonNull(points);
    }

    public Points points(){ return points; }
    @Override public Position position(){ return new Position(liana, height); }
}
