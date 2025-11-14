package com.dkj.model;


import java.util.Objects;

public final class Position {
    private final LianaId liana;
    private final Height  height;
    public Position(LianaId liana, Height height){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
    }
    public LianaId liana(){ return liana; }
    public Height  height(){ return height; }
    @Override public String toString(){ return "l="+liana+",h="+height; }
}
