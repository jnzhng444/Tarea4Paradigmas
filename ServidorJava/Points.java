
import java.util.Objects;

public final class Points {
    private final String value;
    public Points(String value){
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Points");
        this.value = value;
    }
    public String value(){ return value; }
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof Points p) && value.equals(p.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
