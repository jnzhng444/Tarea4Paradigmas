

public final class GameRules {
    public static final int MAX_LIANAS = 6;
    public static final int HEIGHT_MAX = 12;

    // Límites del mundo en píxeles
    public static final float MIN_X = 20.0f;
    public static final float MAX_X = 780.0f;
    public static final float MIN_Y = 0.0f;
    public static final float MAX_Y = 540.0f;

    // Física
    public static final float GRAVITY     = 800.0f;
    public static final float MOVE_SPEED  = 200.0f;
    public static final float JUMP_FORCE  = 350.0f;
    public static final float CLIMB_SPEED = 150.0f;

    private GameRules() {}
}
