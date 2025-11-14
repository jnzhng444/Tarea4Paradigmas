package com.dkj.game;

import com.dkj.entities.CrocodileBlue;
import com.dkj.entities.CrocodileRed;
import com.dkj.entities.Fruit;
import com.dkj.model.Height;
import com.dkj.model.Liana;
import com.dkj.model.LianaId;
import com.dkj.model.Platform;

import java.util.*;

public final class Level {

    // Slot con ID estable + datos geométricos
    static final class LianaSlot {
        final LianaId id;          // "1", "2", ...
        final Liana    liana;      // x, top, bottom
        LianaSlot(LianaId id, Liana liana){ this.id = id; this.liana = liana; }
    }

    private final List<Platform>  platforms = new ArrayList<>();
    private final List<LianaSlot> lianaSlots = new ArrayList<>();
    private final Map<String, Integer> idToIndex = new HashMap<>(); // "1" -> 0, etc.

    // === NUEVO: entidades dinámicas ===
    private final List<CrocodileRed> redCrocodiles = new ArrayList<>();
    private final List<CrocodileBlue> blueCrocodiles = new ArrayList<>();
    private final List<Fruit> fruitList = new ArrayList<>();
    
    // Posición de Mario (calculada desde DK)
    private Float marioX;
    private Float marioY;

    Level() {
        // Plataformas
        platforms.add(new Platform(Float.valueOf(50), Float.valueOf(520), Float.valueOf(200), Float.valueOf(20)));      // piso
        platforms.add(new Platform(Float.valueOf(330), Float.valueOf(500), Float.valueOf(100), Float.valueOf(20)));    
        platforms.add(new Platform(Float.valueOf(475), Float.valueOf(510), Float.valueOf(90), Float.valueOf(20)));     
        platforms.add(new Platform(Float.valueOf(600), Float.valueOf(510), Float.valueOf(100), Float.valueOf(20)));
        
         // nivel 2
        platforms.add(new Platform(Float.valueOf(240), Float.valueOf(340), Float.valueOf(180), Float.valueOf(15)));
        platforms.add(new Platform(Float.valueOf(570), Float.valueOf(300), Float.valueOf(180), Float.valueOf(15)));

        platforms.add(new Platform(Float.valueOf(240), Float.valueOf(250), Float.valueOf(200), Float.valueOf(15)));     // nivel 3

        platforms.add(new Platform(Float.valueOf(500), Float.valueOf(145), Float.valueOf(170), Float.valueOf(15)));     // nivel 4

        platforms.add(new Platform(Float.valueOf(40), Float.valueOf(130), Float.valueOf(500), Float.valueOf(15)));     // nivel 5
        
        platforms.add(new Platform(Float.valueOf(150), Float.valueOf(50), Float.valueOf(50), Float.valueOf(15)));     // Mini plataforma de victoria

        // Posición de Mario (al lado de DK)
        // DK está en x=80 (40+40), y=110 (130-20)
        // Mario está 60px a la derecha y 5px más abajo
        this.marioX = Float.valueOf(140.0f);  // 80 + 60
        this.marioY = Float.valueOf(115.0f);  // 110 + 5

        // Lianas personalizables - Formato: (x, topY, bottomY)
        // Puedes cambiar topY y bottomY para cada liana individualmente
        
        // Liana 1 (x=100)
        lianaSlots.add(new LianaSlot(new LianaId("1"), new Liana(Float.valueOf(100.0f), Float.valueOf(140.0f), Float.valueOf(520.0f))));
        idToIndex.put("1", Integer.valueOf(0));
        
        // Liana 2 (x=220)
        lianaSlots.add(new LianaSlot(new LianaId("2"), new Liana(Float.valueOf(220.0f), Float.valueOf(140.0f), Float.valueOf(520.0f))));
        idToIndex.put("2", Integer.valueOf(1));
        
        // Liana 3 (x=340)
        lianaSlots.add(new LianaSlot(new LianaId("3"), new Liana(Float.valueOf(340.0f), Float.valueOf(240.0f), Float.valueOf(520.0f))));
        idToIndex.put("3", Integer.valueOf(2));
        
        // Liana 4 (x=460)
        lianaSlots.add(new LianaSlot(new LianaId("4"), new Liana(Float.valueOf(460.0f), Float.valueOf(140.0f), Float.valueOf(520.0f))));
        idToIndex.put("4", Integer.valueOf(3));
        
        // Liana 5 (x=580)
        lianaSlots.add(new LianaSlot(new LianaId("5"), new Liana(Float.valueOf(540.0f), Float.valueOf(160.0f), Float.valueOf(520.0f))));
        idToIndex.put("5", Integer.valueOf(4));
        
        // Liana 6 (x=700)
        lianaSlots.add(new LianaSlot(new LianaId("6"), new Liana(Float.valueOf(700.0f), Float.valueOf(80.0f), Float.valueOf(520.0f))));
        idToIndex.put("6", Integer.valueOf(5));
        
        // Liana 7 - Mini liana para acceder a la llave y plataforma de victoria (x=310)
        lianaSlots.add(new LianaSlot(new LianaId("7"), new Liana(Float.valueOf(220.0f), Float.valueOf(50.0f), Float.valueOf(90.0f))));
        idToIndex.put("7", Integer.valueOf(6));
    }

    public List<Platform> platforms() { return platforms; }
    
    // Posición de Mario
    public Float marioX() { return marioX; }
    public Float marioY() { return marioY; }

    // === Lianas (API pública del Level) ===
    public Integer lianaCount() { return lianaSlots.size(); }

    public Boolean hasLiana(LianaId id) { return idToIndex.containsKey(id.value()); }

    public OptionalInt indexOf(LianaId id) {
        Integer idx = idToIndex.get(id.value());
        return (idx == null) ? OptionalInt.empty() : OptionalInt.of(idx);
    }

    public Optional<Liana> lianaById(LianaId id) {
        var idx = indexOf(id);
        if (idx.isEmpty()) return Optional.empty();
        return Optional.of(lianaSlots.get(idx.getAsInt()).liana);
    }

    public Float xOf(LianaId id) {
        var li = lianaById(id).orElseThrow(() ->
            new IllegalArgumentException("Liana no existe: " + id.value()));
        return li.x;
    }

    // Nueva función: obtener topY de una liana específica
    public Float topYOf(LianaId id) {
        var li = lianaById(id).orElseThrow(() ->
            new IllegalArgumentException("Liana no existe: " + id.value()));
        return li.topY;
    }

    // Nueva función: calcular altura lógica inicial para un cocodrilo azul spawneado en una liana
    public Height getInitialHeightForLiana(LianaId id) {
        Float topY = topYOf(id);
        // Convertir topY a altura lógica
        // topY=120 -> altura=12, topY=520 -> altura=0
        Float min_y = Float.valueOf(120.0f);
        Float max_y = Float.valueOf(520.0f);
        Float range = max_y - min_y;
        Float logicalHeight = ((max_y - topY) / range) * 12.0f;
        Integer heightInt = Math.round(logicalHeight);
        return new Height(heightInt.toString());
    }

    // NUEVO: Mapear altura lógica (0-12) al espacio real de la liana
    // 0 siempre es el bottomY de la liana, 12 siempre es el topY de la liana
    Height mapHeightToLiana(LianaId id, Height logicalHeight) {
        // Obtener topY y bottomY de la liana
        var li = lianaById(id).orElseThrow(() ->
            new IllegalArgumentException("Liana no existe: " + id.value()));
        
        // Parsear altura solicitada (0-12)
        Integer requested;
        try {
            requested = Integer.parseInt(logicalHeight.value());
        } catch (Exception e) {
            requested = Integer.valueOf(0);
        }
        
        // Clampear al rango 0-12
        requested = Math.max(0, Math.min(12, requested));
        
        // Mapear proporcionalmente: 
        // altura 0 (lógica) -> bottomY de esta liana
        // altura 12 (lógica) -> topY de esta liana
        Float lianaRange = li.bottomY - li.topY;  // Rango en píxeles de esta liana
        Float mappedY = li.bottomY - (requested / 12.0f) * lianaRange;
        
        // Convertir de píxeles de vuelta a altura lógica global (para el motor de física)
        Float global_min_y = Float.valueOf(120.0f);
        Float global_max_y = Float.valueOf(520.0f);
        Float global_range = global_max_y - global_min_y;
        Float globalLogicalHeight = ((global_max_y - mappedY) / global_range) * 12.0f;
        Integer finalHeight = Math.round(globalLogicalHeight);
        
        System.out.println("[LEVEL] Liana " + id.value() + 
                          " - Logical height: " + requested + "/12" +
                          " -> Pixel Y: " + Math.round(mappedY) + 
                          " (liana range: " + Math.round(li.topY) + "-" + Math.round(li.bottomY) + ")" +
                          " -> Global height: " + finalHeight);
        
        return new Height(finalHeight.toString());
    }

    // NUEVO: Obtener altura lógica mínima de una liana (bottomY convertido)
    Float getMinHeightForLiana(LianaId id) {
        var li = lianaById(id).orElseThrow(() ->
            new IllegalArgumentException("Liana no existe: " + id.value()));
        Float min_y = Float.valueOf(120.0f);
        Float max_y = Float.valueOf(520.0f);
        Float range = max_y - min_y;
        return ((max_y - li.bottomY) / range) * 12.0f;
    }

    // NUEVO: Obtener altura lógica máxima de una liana (topY convertido)
    Float getMaxHeightForLiana(LianaId id) {
        var li = lianaById(id).orElseThrow(() ->
            new IllegalArgumentException("Liana no existe: " + id.value()));
        Float min_y = Float.valueOf(120.0f);
        Float max_y = Float.valueOf(520.0f);
        Float range = max_y - min_y;
        return ((max_y - li.topY) / range) * 12.0f;
    }

    // NUEVO: Validar formato de altura (solo que esté en 0-12)
    Boolean isHeightValidFormat(Height requestedHeight) {
        Integer requested;
        try {
            requested = Integer.parseInt(requestedHeight.value());
        } catch (Exception e) {
            System.err.println("[LEVEL] Invalid height format: " + requestedHeight.value());
            return Boolean.FALSE;
        }
        
        // Validar que esté en el rango lógico 0-12
        if (requested < 0 || requested > 12) {
            System.err.println("[LEVEL] Height " + requested + " is outside logical range [0-12]");
            return Boolean.FALSE;
        }
        
        return Boolean.TRUE;
    }

    // Para físicas/jugador (se sigue usando la lista "simple"):
    List<Liana> lianas() {
        List<Liana> list = new ArrayList<>(lianaSlots.size());
        for (var s : lianaSlots) list.add(s.liana);
        return list;
    }

    // === NUEVO: getters públicos de entidades (CORREGIDO - sin recursión) ===
    List<CrocodileRed> crocodileReds() { return redCrocodiles; }      
    List<CrocodileBlue> crocodileBlues() { return blueCrocodiles; }  
    List<Fruit> fruits() { return fruitList; }                        
    
     // Helpers de spawn
    void addFruit(Fruit f){ fruitList.add(Objects.requireNonNull(f)); }
    void addCrocRed(CrocodileRed c){ redCrocodiles.add(Objects.requireNonNull(c)); }
    void addCrocBlue(CrocodileBlue c){ blueCrocodiles.add(Objects.requireNonNull(c)); }
}