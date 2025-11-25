package com.dkj.model;


import java.util.Objects;

/**
 * Altura en una liana (Value Object).
 * 
 * Representa una coordenada vertical discreta en el sistema de juego.
 * Internamente almacena el valor como String para mantener la abstraccion
 * del dominio y evitar exponer primitivos.
 * 
 * Rango tipico: 0-12 (donde 0 es el suelo y 12 es la altura maxima)
 * 
 * Validaciones:
 * - El valor no puede ser null
 * - El valor no puede estar en blanco
 * 
 * Patrones aplicados: Value Object (DDD)
 */
public final class Height {
    private final String value; // no exponemos int en el dominio
    
    /**
     * Crea una nueva altura con el valor especificado.
     * 
     * @param value Valor de la altura como String. No puede ser null ni blank.
     * @throws IllegalArgumentException si value es null o blank
     */
    public Height(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Height");
        this.value = value;
    }
    
    /**
     * Obtiene el valor de la altura.
     * 
     * @return String representando la altura
     */
    public String value(){ return value; }
    
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof Height h) && value.equals(h.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
