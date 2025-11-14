package com.dkj.model;

public final class Liana {
    public final Float x, topY, bottomY;
    public Liana(Float x, Float topY, Float bottomY) {
        this.x = x; this.topY = topY; this.bottomY = bottomY;
    }
    public Boolean canGrab(Float px, Float py, Float range) {
        return Math.abs(px - x) < range && py > topY && py < bottomY;
    }
}