public final class DefaultEntityFactory {

    // Azul: arranca en una plataforma caminando desde la izquierda (SPAWNER AUTOMÁTICO)
    public CrocodileBlue newBlue(Height platformHeight, Speed speed) {
        // Empieza desde el lado izquierdo de la pantalla
        return new CrocodileBlue(platformHeight, speed, GameRules.MIN_X);
    }

    // NUEVO: Azul que arranca directamente en una liana bajando (ADMIN CONSOLE)
    public CrocodileBlue newBlueOnLiana(LianaId liana, Height height, Speed speed) {
        return new CrocodileBlue(liana, height, speed);
    }

    // Rojo: liana + altura + velocidad + límites de la liana
    public CrocodileRed newRed(final LianaId liana, final Height height, final Speed speed, 
                               final Float minHeight, final Float maxHeight) {
        return new CrocodileRed(liana, height, speed, minHeight, maxHeight);
    }

    public Fruit newFruit(final LianaId liana, final Height height, final Points points) {
        return new Fruit(liana, height, points);
    }
}