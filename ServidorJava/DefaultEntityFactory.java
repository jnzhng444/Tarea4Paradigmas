public final class DefaultEntityFactory {

    // Azul: arranca en una plataforma caminando desde la izquierda
    public CrocodileBlue newBlue(Height platformHeight, Speed speed) {
        // Empieza desde el lado izquierdo de la pantalla
        return new CrocodileBlue(platformHeight, speed, GameRules.MIN_X);
    }

    // Rojo: liana + altura + velocidad
    public CrocodileRed newRed(final LianaId liana, final Height height, final Speed speed) {
        return new CrocodileRed(liana, height, speed);
    }

    public Fruit newFruit(final LianaId liana, final Height height, final Points points) {
        return new Fruit(liana, height, points);
    }
}