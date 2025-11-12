import java.util.Objects;

public final class CrocodileRed implements Entity {
    private final LianaId liana;
    private Height  height;
    private final Speed   speed;
    private Boolean goingUp;
    private Float heightFloat;  // NUEVO: mantener altura como float
    
    private static final Integer MIN_HEIGHT = Integer.valueOf(0);
    private static final Integer MAX_HEIGHT = Integer.valueOf(12);

    public CrocodileRed(LianaId liana, Height height, Speed speed){
        this.liana = Objects.requireNonNull(liana);
        this.height = Objects.requireNonNull(height);
        this.speed = Objects.requireNonNull(speed);
        this.goingUp = Boolean.TRUE;
        
        // Inicializar heightFloat
        try {
            this.heightFloat = Float.parseFloat(height.value());
        } catch (Exception e) {
            this.heightFloat = 6.0f;
        }
        
        System.out.println("[RED CREATED] liana=" + liana.value() + " height=" + height.value() + " speed=" + speed.value());
    }
    
    @Override public Position position(){ return new Position(liana, height); }

    public CrocodileRed step(){
        try {
            Float speedValue = Float.parseFloat(speed.value());
            
            // Usar la velocidad directamente (0.3 significa 0.3 unidades lógicas por tick)
            if (goingUp) {
                heightFloat = heightFloat + speedValue;
                
                if (heightFloat >= MAX_HEIGHT.floatValue()) {
                    heightFloat = MAX_HEIGHT.floatValue();
                    goingUp = Boolean.FALSE;
                    System.out.println("[RED] Reached MAX, going DOWN now");
                }
            } else {
                heightFloat = heightFloat - speedValue;
                
                if (heightFloat <= MIN_HEIGHT.floatValue()) {
                    heightFloat = MIN_HEIGHT.floatValue();
                    goingUp = Boolean.TRUE;
                    System.out.println("[RED] Reached MIN, going UP now");
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