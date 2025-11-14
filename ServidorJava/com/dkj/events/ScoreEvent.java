package com.dkj.events;

import com.dkj.model.PlayerId;
import com.dkj.model.Points;

public final class ScoreEvent implements GameEvent {
    private final PlayerId player; 
    private final Points points;
    
    public ScoreEvent(PlayerId p, Points pts){ 
        this.player=p; 
        this.points=pts; 
    }
    
    public PlayerId player(){ return player; } 
    public Points points(){ return points; }
}
