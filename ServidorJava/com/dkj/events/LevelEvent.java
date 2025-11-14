package com.dkj.events;

import com.dkj.model.Speed;

public final class LevelEvent implements GameEvent {
    private final String level; 
    private final Speed speed;
    
    public LevelEvent(String level, Speed speed){ 
        this.level=level; 
        this.speed=speed; 
    }
    
    public String level(){ return level; } 
    public Speed speed(){ return speed; }
}
