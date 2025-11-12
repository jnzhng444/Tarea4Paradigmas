final class Liana {
    final Float x, topY, bottomY;
    Liana(Float x, Float topY, Float bottomY) {
        this.x = x; this.topY = topY; this.bottomY = bottomY;
    }
    Boolean canGrab(Float px, Float py, Float range) {
        return Math.abs(px - x) < range && py > topY && py < bottomY;
    }
}