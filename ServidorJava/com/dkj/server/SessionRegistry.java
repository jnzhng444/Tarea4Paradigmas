package com.dkj.server;



import java.util.List;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registro simple y thread-safe de sesiones conectadas. */
public final class SessionRegistry {
    private final CopyOnWriteArrayList<ClientContext> all = new CopyOnWriteArrayList<>();

    /** Guarda la sesión si no existe (PLAYER). */
    public void addPlayer(ClientContext ctx) {
        if (ctx != null) all.addIfAbsent(ctx);
    }

    /** Guarda la sesión si no existe (SPECTATOR). */
    public void addSpectator(ClientContext ctx) {
        if (ctx != null) all.addIfAbsent(ctx);
    }

    /** Elimina la sesión del registro. */
    public void remove(ClientContext ctx) {
        if (ctx != null) all.remove(ctx);
    }

    /** Snapshot inmutable para iterar desde consola/admin. */
    public List<ClientContext> list() {
        return Collections.unmodifiableList(all);
    }
}
