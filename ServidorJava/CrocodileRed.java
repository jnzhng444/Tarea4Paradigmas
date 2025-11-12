import java.util.Objects;

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
    
    @Override public Position position(){ return new Position(liana, height); }
    
    public Boolean isGoingUp() { return goingUp; }

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