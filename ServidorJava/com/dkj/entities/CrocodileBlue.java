package com.dkj.entities;

import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.Position;
import com.dkj.model.Speed;
import com.dkj.model.Platform;
import com.dkj.game.GameRules;
import com.dkj.game.Level;

import java.util.Objects;
import java.util.Random;

public final class CrocodileBlue implements Entity {
    private LianaId liana;
    private Height  height;
    private final Speed speed;
    private CrocodileBlueState state;
    private Float platformX;
    private Float heightFloat;
    private Integer targetLianaIndex;  // liana objetivo
    private Float targetLianaX;  // posición X objetivo
    private Boolean movingRight;  // dirección de movimiento
    
    private static final Random random = new Random();
    private static final Integer[] VALID_LIANAS = {1, 2, 4, 5, 6}; // Excluir liana 3
    
    public enum CrocodileBlueState {
        WALKING_ON_PLATFORM,
        DESCENDING_LIANA
    }

    // Constructor existente para spawner automático (camina en plataforma)
    public CrocodileBlue(Height platformHeight, Speed speed, Float startX){
        this.liana = null;
        this.height = Objects.requireNonNull(platformHeight);
        this.speed  = Objects.requireNonNull(speed);
        this.state = CrocodileBlueState.WALKING_ON_PLATFORM;
        this.platformX = startX;
        
        try {
            this.heightFloat = Float.parseFloat(platformHeight.value());
        } catch (Exception e) {
            this.heightFloat = Float.valueOf(12.0f);
        }
        
        // Elegir una liana aleatoria de las válidas (1, 2, 4, 5, 6 - sin liana 3)
        this.targetLianaIndex = VALID_LIANAS[random.nextInt(VALID_LIANAS.length)];
        this.targetLianaX = null;  // Se calculará después
        this.movingRight = Boolean.TRUE;  // Por defecto
        
        System.out.println("[BLUE CREATED WALKING] Target liana: " + targetLianaIndex + ", height=" + heightFloat + ", x=" + startX);
    }

    // NUEVO: Constructor para spawneo directo en liana (ADMIN CONSOLE)
    public CrocodileBlue(LianaId liana, Height height, Speed speed){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.speed  = Objects.requireNonNull(speed);
        this.state = CrocodileBlueState.DESCENDING_LIANA; // Ya empieza bajando
        this.platformX = null; // No aplica
        this.targetLianaIndex = null; // No necesita target, ya está en liana
        this.targetLianaX = null;
        this.movingRight = null;
        
        try {
            this.heightFloat = Float.parseFloat(height.value());
        } catch (Exception e) {
            this.heightFloat = Float.valueOf(12.0f);
        }
        
        System.out.println("[BLUE CREATED ON LIANA] liana=" + liana.value() + ", height=" + heightFloat + " (ADMIN SPAWN)");
    }
    
    @Override 
    public Position position(){ 
        return new Position(
            liana != null ? liana : new LianaId("0"), 
            height
        ); 
    }
    
    public Float getPlatformX() { return platformX; }
    public CrocodileBlueState getState() { return state; }

    public CrocodileBlue step(Level level){
        try {
            Float speedValue = Float.parseFloat(speed.value());
            
            if (state == CrocodileBlueState.WALKING_ON_PLATFORM) {
                LianaId targetLiana = new LianaId(targetLianaIndex.toString());
                
                // Calcular targetLianaX y dirección si aún no se ha hecho
                if (targetLianaX == null && level.hasLiana(targetLiana)) {
                    targetLianaX = level.xOf(targetLiana);
                    movingRight = platformX < targetLianaX;
                    System.out.println("[BLUE] Target liana " + targetLianaIndex + " at x=" + targetLianaX + 
                                     ", moving " + (movingRight ? "RIGHT" : "LEFT") + " from x=" + platformX);
                }
                
                // Mover en la dirección correcta
                Float moveSpeed = speedValue * Float.valueOf(50.0f);
                if (movingRight) {
                    platformX = platformX + moveSpeed;
                } else {
                    platformX = platformX - moveSpeed;
                }
                
                // Verificar si necesitamos bajar a la siguiente plataforma
                // Esto simula "caer" al borde de una plataforma
                // Convertir altura lógica a Y píxeles aproximado (altura 12=120px, altura 0=520px)
                Float currentY = Float.valueOf(520.0f) - (heightFloat / 12.0f) * 400.0f;
                
                // Buscar si hay plataforma debajo de nuestra posición actual
                boolean onPlatform = false;
                Float targetY = currentY;
                
                for (Platform p : level.platforms()) {
                    // Verificar si estamos horizontalmente sobre esta plataforma
                    if (platformX >= p.x && platformX <= p.x + p.w) {
                        Float platformTop = p.y;
                        // Ajustar para estar en el borde superior (restar altura del sprite)
                        Float platformSurface = platformTop - Float.valueOf(20.0f); // 20px = mitad altura del cocodrilo
                        
                        // Si la plataforma está debajo nuestro (hasta 80 píxeles), bajar a ella
                        if (platformSurface > currentY && platformSurface < currentY + Float.valueOf(80.0f)) {
                            targetY = platformSurface;
                            onPlatform = true;
                        }
                        // Si estamos encima de esta plataforma (dentro de 30 píxeles), quedarnos ahí
                        else if (Math.abs(currentY - platformSurface) < Float.valueOf(30.0f)) {
                            targetY = platformSurface;
                            onPlatform = true;
                        }
                    }
                }
                
                // Actualizar altura basada en la plataforma encontrada
                if (onPlatform) {
                    // Convertir Y píxeles de vuelta a altura lógica
                    heightFloat = ((Float.valueOf(520.0f) - targetY) / 400.0f) * 12.0f;
                    if (heightFloat < Float.valueOf(0.0f)) heightFloat = Float.valueOf(0.0f);
                    if (heightFloat > Float.valueOf(12.0f)) heightFloat = Float.valueOf(12.0f);
                }
                
                Integer heightInt = Math.round(heightFloat);
                this.height = new Height(heightInt.toString());
                
                if (level.hasLiana(targetLiana) && targetLianaX != null) {
                    // Detección mejorada con mayor tolerancia
                    Float distance = Math.abs(platformX - targetLianaX);
                    
                    if (distance < Float.valueOf(25.0f)) {
                        this.liana = targetLiana;
                        this.platformX = targetLianaX;
                        this.state = CrocodileBlueState.DESCENDING_LIANA;
                        
                        // Usar la altura inicial de la liana específica (basada en su topY)
                        Height initialHeight = level.getInitialHeightForLiana(targetLiana);
                        this.height = initialHeight;
                        
                        try {
                            this.heightFloat = Float.parseFloat(initialHeight.value());
                        } catch (Exception e) {
                            this.heightFloat = Float.valueOf(12.0f);
                        }
                        
                        System.out.println("[BLUE] Reached target! Descending liana " + targetLiana.value() + 
                                         " at x=" + platformX + " from height=" + initialHeight.value());
                        return this;
                    }
                }
                
                // Si sale de los límites, desaparece
                if (platformX > GameRules.MAX_X) {
                    System.out.println("[BLUE] Disappeared at right edge at x=" + platformX);
                    return null;
                }
                if (platformX < GameRules.MIN_X) {
                    System.out.println("[BLUE] Disappeared at left edge at x=" + platformX);
                    return null;
                }
                
                return this;
                
            } else {
                // DESCENDING_LIANA
                // Usar la misma velocidad que el rojo para descender
                heightFloat = heightFloat - speedValue;
                
                Integer heightInt = Math.round(heightFloat);
                this.height = new Height(heightInt.toString());
                
                if (heightFloat <= Float.valueOf(0.0f)) {
                    System.out.println("[BLUE] Disappeared at bottom");
                    return null;
                }
                
                return this;
            }
        } catch (Exception e) {
            System.err.println("[BLUE ERROR] " + e.getMessage());
            e.printStackTrace();
            return this;
        }
    }
}