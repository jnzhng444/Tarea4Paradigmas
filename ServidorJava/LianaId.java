

import java.util.Objects;

public final class LianaId {
    private final String value;
    public LianaId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("LianaId");
        this.value = value;
    }
    public String value() { return value; }
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof LianaId l) && value.equals(l.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
