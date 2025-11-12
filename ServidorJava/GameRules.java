public final class GameRules {
    public static final Integer MAX_LIANAS = Integer.valueOf(6);
    public static final Integer HEIGHT_MAX = Integer.valueOf(12);

    // Límites del mundo en píxeles
    public static final Float MIN_X = Float.valueOf(20.0f);
    public static final Float MAX_X = Float.valueOf(780.0f);
    public static final Float MIN_Y = Float.valueOf(0.0f);
    public static final Float MAX_Y = Float.valueOf(600.0f);

    // Física
    public static final Float GRAVITY     = Float.valueOf(470.0f);
    public static final Float MOVE_SPEED  = Float.valueOf(50.0f);
    public static final Float JUMP_FORCE  = Float.valueOf(280.0f);
    public static final Float CLIMB_SPEED = Float.valueOf(75.0f);

    public static final Float MAX_FALL_SPEED = Float.valueOf(400.0f);  // Velocidad máxima de caída

    private GameRules() {}
}