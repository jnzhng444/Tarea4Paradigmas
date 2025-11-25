package com.dkj.model;



import java.util.Objects;

/**
 * Velocidad de entidades en el juego (Value Object).
 * 
 * Representa la velocidad de movimiento de enemigos (cocodrilos) en unidades
 * por tick de juego. Internamente almacena el valor como String para mantener
 * la abstraccion del dominio y evitar exponer primitivos.
 * 
 * Ejemplo: "0.03" representa un movimiento lento, "0.06" seria mas rapido.
 * 
 * Validaciones:
 * - El valor no puede ser null
 * - El valor no puede estar en blanco
 * 
 * Patrones aplicados: Value Object (DDD)
 */
public final class Speed {
    private final String unitsPerTick; // p.ej. "1" o "2" (sin primitivo a la vista)
    
    /**
     * Crea un nuevo objeto Speed con el valor especificado.
     * 
     * @param unitsPerTick Velocidad en unidades por tick como String. No puede ser null ni blank.
     * @throws IllegalArgumentException si unitsPerTick es null o blank
     */
    public Speed(String unitsPerTick){
        if (unitsPerTick == null || unitsPerTick.isBlank()) throw new IllegalArgumentException("Speed");
        this.unitsPerTick = unitsPerTick;
    }
    
    /**
     * Obtiene el valor de la velocidad.
     * 
     * @return String representando las unidades por tick
     */
    public String value(){ return unitsPerTick; }
    
    @Override public String toString(){ return unitsPerTick; }
    @Override public boolean equals(Object o){ return (o instanceof Speed s) && unitsPerTick.equals(s.unitsPerTick); }
    @Override public int hashCode(){ return Objects.hash(unitsPerTick); }
}
