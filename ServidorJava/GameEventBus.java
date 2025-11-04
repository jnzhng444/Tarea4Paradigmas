package ServidorJava;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameEventBus {
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    public void subscribe(GameEventListener l){ listeners.add(l); }
    public void unsubscribe(GameEventListener l){ listeners.remove(l); }
    public void emit(GameEvent e){ for (var l : listeners) l.onEvent(e); }
}
