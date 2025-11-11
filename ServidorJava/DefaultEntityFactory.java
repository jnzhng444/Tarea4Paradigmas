

/** Fábrica concreta de entidades del dominio */
public final class DefaultEntityFactory {

    /** Azul: arranca en altura MAX y con la velocidad actual. */
    public CrocodileBlue newBlue(final LianaId liana, final Speed speed) {
        return new CrocodileBlue(liana, new Height("MAX"), speed);
    }

    /** Rojo: liana + altura + velocidad actual. */
    public CrocodileRed newRed(final LianaId liana, final Height height, final Speed speed) {
        return new CrocodileRed(liana, height, speed);
    }

    /** Fruta: liana + altura + puntos. */
    public Fruit newFruit(final LianaId liana, final Height height, final Points points) {
        return new Fruit(liana, height, points);
    }
}
