package com.dkj.events;

public final class StateEvent implements GameEvent {
    private final String payload; // línea STATE lista para enviar
    public StateEvent(String payload){ this.payload = payload; }
    public String payload(){ return payload; }
}
