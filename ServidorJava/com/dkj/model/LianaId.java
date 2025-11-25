package com.dkj.model;



import java.util.Objects;

/**
 * Identificador de liana (Value Object).
 * 
 * Representa el identificador unico de una liana en el nivel de juego.
 * Las lianas son estructuras verticales por las que el jugador puede trepar.
 * El identificador es inmutable y no puede ser vacio.
 * 
 * Validaciones:
 * - El valor no puede ser null
 * - El valor no puede estar en blanco
 * 
 * Patrones aplicados: Value Object (DDD)
 */
public final class LianaId {
    private final String value;
    
    /**
     * Crea un nuevo identificador de liana con el valor especificado.
     * 
     * @param value Valor del identificador. No puede ser null ni blank.
     * @throws IllegalArgumentException si value es null o blank
     */
    public LianaId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("LianaId");
        this.value = value;
    }
    
    /**
     * Obtiene el valor del identificador.
     * 
     * @return String representando el identificador de la liana
     */
    public String value() { return value; }
    
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof LianaId l) && value.equals(l.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
