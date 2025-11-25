package com.dkj.model;



import java.util.Objects;
import java.util.UUID;

/**
 * Identificador unico de jugador (Value Object).
 * 
 * Representa la identidad de un jugador en el sistema mediante un identificador
 * unico inmutable. Utiliza UUID para generar identificadores aleatorios o puede
 * construirse con un valor especifico.
 * 
 * Caracteristicas:
 * - Inmutable: Una vez creado, el valor no puede cambiar
 * - Comparacion por valor: Dos PlayerId son iguales si tienen el mismo valor
 * - Thread-safe: Al ser inmutable, puede compartirse entre hilos sin sincronizacion
 * 
 * Patrones aplicados: Value Object (DDD)
 */
public final class PlayerId {
    private final String value;
    
    /**
     * Crea un nuevo PlayerId con un UUID aleatorio.
     */
    public PlayerId(){ this.value = UUID.randomUUID().toString(); }
    
    /**
     * Crea un PlayerId con el valor especificado.
     * 
     * @param value Valor del identificador. No puede ser null.
     * @throws NullPointerException si value es null
     */
    public PlayerId(String value){ this.value = Objects.requireNonNull(value); }
    
    /**
     * Obtiene el valor del identificador.
     * 
     * @return String representando el identificador unico del jugador
     */
    public String value(){ return value; }
    
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof PlayerId p) && value.equals(p.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
