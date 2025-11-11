

import java.util.Objects;
import java.util.UUID;

public final class PlayerId {
    private final String value;
    public PlayerId(){ this.value = UUID.randomUUID().toString(); }
    public PlayerId(String value){ this.value = Objects.requireNonNull(value); }
    public String value(){ return value; }
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof PlayerId p) && value.equals(p.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
