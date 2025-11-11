
public enum Direction {
    UP, DOWN, LEFT, RIGHT, JUMP;

    public static Direction fromString(String s) {
        try {
            return Direction.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
