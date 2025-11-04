package ServidorJava;

public sealed interface GameEvent permits StateEvent, ScoreEvent, DeathEvent, LevelEvent {}

final class StateEvent implements GameEvent {
    private final String payload; // línea STATE lista para enviar
    StateEvent(String payload){ this.payload = payload; }
    public String payload(){ return payload; }
}
final class ScoreEvent implements GameEvent {
    private final PlayerId player; private final Points points;
    ScoreEvent(PlayerId p, Points pts){ this.player=p; this.points=pts; }
    public PlayerId player(){ return player; } public Points points(){ return points; }
}
final class DeathEvent implements GameEvent {
    private final PlayerId player;
    DeathEvent(PlayerId p){ this.player=p; }
    public PlayerId player(){ return player; }
}
final class LevelEvent implements GameEvent {
    private final String level; private final Speed speed;
    LevelEvent(String level, Speed speed){ this.level=level; this.speed=speed; }
    public String level(){ return level; } public Speed speed(){ return speed; }
}
