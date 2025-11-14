package com.dkj.events;

import com.dkj.model.PlayerId;

public final class DeathEvent implements GameEvent {
    private final PlayerId player;
    
    public DeathEvent(PlayerId p){ 
        this.player=p; 
    }
    
    public PlayerId player(){ return player; }
}
