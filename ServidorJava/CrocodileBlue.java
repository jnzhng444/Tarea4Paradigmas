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
    
    private static final Random random = new Random();
    
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
        
        // Elegir una liana aleatoria como objetivo (1-6)
        this.targetLianaIndex = Integer.valueOf(random.nextInt(6) + 1);
        
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
            if (state == CrocodileBlueState.WALKING_ON_PLATFORM) {
                Float moveSpeed = Float.valueOf(1.5f);
                platformX = platformX + moveSpeed;
                
                // Solo verificar la liana objetivo
                LianaId targetLiana = new LianaId(targetLianaIndex.toString());
                
                if (level.hasLiana(targetLiana)) {
                    Float targetX = level.xOf(targetLiana);
                    
                    // Si llegamos a la liana objetivo
                    if (Math.abs(platformX - targetX) < Float.valueOf(5.0f)) {
                        this.liana = targetLiana;
                        this.platformX = targetX;
                        this.state = CrocodileBlueState.DESCENDING_LIANA;
                        
                        try {
                            this.heightFloat = Float.parseFloat(height.value());
                        } catch (Exception e) {
                            this.heightFloat = Float.valueOf(12.0f);
                        }
                        
                        System.out.println("[BLUE] Started descending on target liana " + targetLiana.value() + " at x=" + platformX);
                        return this;
                    }
                }
                
                // Si sale del límite derecho, desaparece
                if (platformX > GameRules.MAX_X) {
                    System.out.println("[BLUE] Disappeared at right edge at x=" + platformX);
                    return null;
                }
                
                return this;
                
            } else {
                // DESCENDING_LIANA
                Float descentSpeed = Float.valueOf(0.1f);
                heightFloat = heightFloat - descentSpeed;
                
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