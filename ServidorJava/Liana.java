package ServidorJava;

final class Liana {
    final float x, topY, bottomY;
    Liana(float x, float topY, float bottomY) {
        this.x = x; this.topY = topY; this.bottomY = bottomY;
    }
    boolean canGrab(float px, float py, float range) {
        return Math.abs(px - x) < range && py > topY && py < bottomY;
    }
}
