

import java.util.Objects;

public final class Speed {
    private final String unitsPerTick; // p.ej. "1" o "2" (sin primitivo a la vista)
    public Speed(String unitsPerTick){
        if (unitsPerTick == null || unitsPerTick.isBlank()) throw new IllegalArgumentException("Speed");
        this.unitsPerTick = unitsPerTick;
    }
    public String value(){ return unitsPerTick; }
    @Override public String toString(){ return unitsPerTick; }
    @Override public boolean equals(Object o){ return (o instanceof Speed s) && unitsPerTick.equals(s.unitsPerTick); }
    @Override public int hashCode(){ return Objects.hash(unitsPerTick); }
}
