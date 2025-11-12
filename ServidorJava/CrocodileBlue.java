import java.util.Objects;

public final class CrocodileBlue implements Entity {
    private LianaId liana;
    private Height  height;
    private final Speed speed;
    private CrocodileBlueState state;
    private Float platformX;
    private Float heightFloat;
    
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
        
        System.out.println("[BLUE CREATED] Initial state: WALKING, height=" + heightFloat + ", x=" + startX);
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
                
                for (Integer i = Integer.valueOf(0); i < level.lianaCount(); i = i + 1) {
                    LianaId lianaId = new LianaId(String.valueOf(i + 1));
                    Float lianaX = level.xOf(lianaId);
                    
                    // CAMBIO: Rango más estrecho (de 15 a 5) para que solo agarre cuando esté MUY cerca
                    if (Math.abs(platformX - lianaX) < 5.0f) {
                        this.liana = lianaId;
                        this.platformX = lianaX;
                        this.state = CrocodileBlueState.DESCENDING_LIANA;
                        
                        try {
                            this.heightFloat = Float.parseFloat(height.value());
                        } catch (Exception e) {
                            this.heightFloat = 12.0f;
                        }
                        
                        System.out.println("[BLUE] Started descending on liana " + lianaId.value() + " at x=" + platformX);
                        return this;
                    }
                }
                
                if (platformX > GameRules.MAX_X) {
                    System.out.println("[BLUE] Disappeared at right edge at x=" + platformX);
                    return null;
                }
                
                // Log para ver el azul caminando
                if (Math.random() < 0.02) {
                    System.out.println("[BLUE WALKING] x=" + platformX);
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