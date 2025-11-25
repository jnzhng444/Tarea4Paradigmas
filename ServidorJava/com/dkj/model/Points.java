package com.dkj.model;


import java.util.Objects;

/**
 * Puntos obtenidos en el juego (Value Object).
 * 
 * Representa una cantidad de puntos que puede ser asignada a frutas o acumulada
 * por un jugador. Internamente almacena el valor como String para mantener la
 * abstraccion del dominio.
 * 
 * Uso tipico: Puntos de frutas (100, 200, etc.)
 * 
 * Validaciones:
 * - El valor no puede ser null
 * - El valor no puede estar en blanco
 * 
 * Patrones aplicados: Value Object (DDD)
 */
public final class Points {
    private final String value;
    
    /**
     * Crea un nuevo objeto Points con el valor especificado.
     * 
     * @param value Valor de los puntos como String. No puede ser null ni blank.
     * @throws IllegalArgumentException si value es null o blank
     */
    public Points(String value){
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Points");
        this.value = value;
    }
    
    /**
     * Obtiene el valor de los puntos.
     * 
     * @return String representando la cantidad de puntos
     */
    public String value(){ return value; }
    
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof Points p) && value.equals(p.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
