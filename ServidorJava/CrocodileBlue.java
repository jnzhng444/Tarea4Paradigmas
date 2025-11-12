import java.util.Objects;
import java.util.Random;

public final class CrocodileBlue implements Entity {
    private LianaId liana;
    private Height  height;
    private final Speed speed;
    private CrocodileBlueState state;
    private Float platformX;
    private Float heightFloat;
    private Integer targetLianaIndex;  // NUEVO: liana objetivo
    
    private static final Random random = new Random();
    
    public enum CrocodileBlueState {
        WALKING_ON_PLATFORM,
        DESCENDING_LIANA
    }

    public CrocodileBlue(Height platformHeight, Speed speed, Float startX){
        this.liana = null;
        this.height = Objects.requireNonNull(platformHeight);
        this.speed  = Objects.requireNonNull(speed);
        this.state = CrocodileBlueState.WALKING_ON_PLATFORM;
        this.platformX = startX;
        
        try {
            this.heightFloat = Float.parseFloat(platformHeight.value());
        } catch (Exception e) {
            this.heightFloat = 12.0f;
        }
        
        // NUEVO: Elegir una liana aleatoria como objetivo (1-6)
        this.targetLianaIndex = Integer.valueOf(random.nextInt(6) + 1);
        
        System.out.println("[BLUE CREATED] Target liana: " + targetLianaIndex + ", height=" + heightFloat + ", x=" + startX);
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
                Float moveSpeed = 1.5f;
                platformX = platformX + moveSpeed;
                
                // CAMBIO: Solo verificar la liana objetivo
                LianaId targetLiana = new LianaId(targetLianaIndex.toString());
                
                if (level.hasLiana(targetLiana)) {
                    Float targetX = level.xOf(targetLiana);
                    
                    // Si llegamos a la liana objetivo
                    if (Math.abs(platformX - targetX) < 5.0f) {
                        this.liana = targetLiana;
                        this.platformX = targetX;
                        this.state = CrocodileBlueState.DESCENDING_LIANA;
                        
                        try {
                            this.heightFloat = Float.parseFloat(height.value());
                        } catch (Exception e) {
                            this.heightFloat = 12.0f;
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
                Float descentSpeed = 0.1f;
                heightFloat = heightFloat - descentSpeed;
                
                Integer heightInt = Math.round(heightFloat);
                this.height = new Height(heightInt.toString());
                
                if (heightFloat <= 0.0f) {
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