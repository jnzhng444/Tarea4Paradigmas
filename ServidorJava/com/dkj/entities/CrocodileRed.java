package com.dkj.entities;

import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.Position;
import com.dkj.model.Speed;

import java.util.Objects;

/**
 * Cocodrilo rojo - Enemigo que patrulla verticalmente en lianas.
 * 
 * Representa un enemigo que se mueve de forma continua hacia arriba y abajo
 * en una liana especifica, rebotando entre limites verticales. Este es uno
 * de los dos tipos de enemigos en Donkey Kong Jr.
 * 
 * Comportamiento:
 * - Movimiento vertical continuo en una sola liana
 * - Cambia de direccion al alcanzar limites superior o inferior
 * - Velocidad configurable mediante Speed
 * - Usa interpolacion float para movimiento suave
 * 
 * Sistema de coordenadas:
 * - heightFloat: Posicion vertical continua para movimiento suave
 * - height: Posicion discreta para sincronizacion con clientes
 * - minHeight/maxHeight: Limites de patrullaje (pixeles)
 * 
 * Implementa Entity para integrarse en el sistema de entidades del juego.
 */
public final class CrocodileRed implements Entity {
    private final LianaId liana;
    private Height  height;
    private final Speed   speed;
    private Boolean goingUp;
    private Float heightFloat;  // NUEVO: mantener altura como float
    private Float minHeight;    // Límite inferior de esta liana específica
    private Float maxHeight;    // Límite superior de esta liana específica
    
    private static final Integer GLOBAL_MIN_HEIGHT = Integer.valueOf(0);
    private static final Integer GLOBAL_MAX_HEIGHT = Integer.valueOf(12);

    /**
     * Crea un cocodrilo rojo con parametros de patrullaje especificados.
     * 
     * El cocodrilo comienza moviendose hacia arriba desde la altura inicial,
     * y patrullara entre minH y maxH indefinidamente.
     * 
     * @param liana Identificador de la liana a patrullar. No puede ser null.
     * @param height Altura inicial en la liana. No puede ser null.
     * @param speed Velocidad de movimiento vertical. No puede ser null.
     * @param minH Limite inferior de patrullaje (en altura logica)
     * @param maxH Limite superior de patrullaje (en altura logica)
     * @throws NullPointerException si liana, height o speed son null
     */
    public CrocodileRed(LianaId liana, Height height, Speed speed, Float minH, Float maxH){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.speed = Objects.requireNonNull(speed);
        this.goingUp = Boolean.TRUE;
        this.minHeight = minH;
        this.maxHeight = maxH;
        
        // Inicializar heightFloat
        try {
            this.heightFloat = Float.parseFloat(height.value());
        } catch (Exception e) {
            this.heightFloat = (minH + maxH) / 2.0f;  // Default al medio del rango
        }
        
        System.out.println("[RED CREATED] liana=" + liana.value() + 
                          " height=" + height.value() + 
                          " speed=" + speed.value() + 
                          " range=[" + minH + "-" + maxH + "]");
    }
    
    /**
     * Obtiene la posicion actual del cocodrilo.
     * 
     * @return Position en el espacio del juego (liana + altura)
     */
    @Override public Position position(){ return new Position(liana, height); }
    
    /**
     * Indica si el cocodrilo se esta moviendo hacia arriba.
     * 
     * @return true si se mueve hacia arriba, false si se mueve hacia abajo
     */
    public Boolean isGoingUp() { return goingUp; }

    /**
     * Avanza la simulacion del cocodrilo un tick de juego.
     * 
     * Actualiza la posicion vertical del cocodrilo segun su velocidad y direccion.
     * Al alcanzar los limites superior o inferior de patrullaje, invierte la direccion.
     * Utiliza interpolacion float para movimiento suave y luego redondea a entero
     * para sincronizacion con clientes.
     * 
     * @return this (instancia actualizada) para permitir encadenamiento
     */
    public CrocodileRed step(){
        try {
            Float speedValue = Float.parseFloat(speed.value());
            
            // Usar la velocidad directamente (0.3 significa 0.3 unidades lógicas por tick)
            if (goingUp) {
                heightFloat = heightFloat + speedValue;
                
                if (heightFloat >= maxHeight) {
                    heightFloat = maxHeight;
                    goingUp = Boolean.FALSE;
                    System.out.println("[RED] Reached MAX (" + maxHeight + "), going DOWN now");
                }
            } else {
                heightFloat = heightFloat - speedValue;
                
                if (heightFloat <= minHeight) {
                    heightFloat = minHeight;
                    goingUp = Boolean.TRUE;
                    System.out.println("[RED] Reached MIN (" + minHeight + "), going UP now");
                }
            }
            
            // Actualizar altura como entero para el snapshot
            Integer heightInt = Math.round(heightFloat);
            this.height = new Height(heightInt.toString());
            
            // Log menos frecuente para no saturar (solo cada 20 ticks)
            if (Math.random() < 0.05) {
                System.out.println("[RED] heightFloat=" + heightFloat + " heightInt=" + heightInt + " goingUp=" + goingUp);
            }
            
        } catch (Exception e) {
            System.err.println("[RED ERROR] " + e.getMessage());
            e.printStackTrace();
        }
        return this;
    }
}