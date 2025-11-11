

import java.util.*;

final class Level {

    // Slot con ID estable + datos geométricos
    static final class LianaSlot {
        final LianaId id;          // "1", "2", ...
        final Liana    liana;      // x, top, bottom
        LianaSlot(LianaId id, Liana liana){ this.id = id; this.liana = liana; }
    }

    private final List<Platform>  platforms = new ArrayList<>();
    private final List<LianaSlot> lianaSlots = new ArrayList<>();
    private final Map<String, Integer> idToIndex = new HashMap<>(); // "1" -> 0, etc.

    Level() {
        // Plataformas
        platforms.add(new Platform(50, 520, 700, 20));      // piso
        platforms.add(new Platform(80, 420, 200, 15));      // nivel 1
        platforms.add(new Platform(520, 420, 200, 15));
        platforms.add(new Platform(50, 320, 180, 15));      // nivel 2
        platforms.add(new Platform(310, 320, 180, 15));
        platforms.add(new Platform(570, 320, 180, 15));
        platforms.add(new Platform(130, 220, 200, 15));     // nivel 3
        platforms.add(new Platform(470, 220, 200, 15));
        platforms.add(new Platform(200, 120, 400, 15));     // nivel 4
        platforms.add(new Platform(300, 40, 200, 20));      // meta (DK)

        // Lianas con IDs "1".."N"
        for (int i = 0; i < 6; i++) {
            float x = 100.0f + i * 120.0f;
            LianaId id = new LianaId(String.valueOf(i + 1));
            Liana    li = new Liana(x, 30.0f, 540.0f);
            lianaSlots.add(new LianaSlot(id, li));
            idToIndex.put(id.value(), i);
        }
    }

    List<Platform> platforms() { return platforms; }

    // === Lianas (API pública del Level) ===
    int lianaCount() { return lianaSlots.size(); }

    boolean hasLiana(LianaId id) { return idToIndex.containsKey(id.value()); }

    OptionalInt indexOf(LianaId id) {
        Integer idx = idToIndex.get(id.value());
        return (idx == null) ? OptionalInt.empty() : OptionalInt.of(idx);
    }

    Optional<Liana> lianaById(LianaId id) {
        var idx = indexOf(id);
        if (idx.isEmpty()) return Optional.empty();
        return Optional.of(lianaSlots.get(idx.getAsInt()).liana);
    }

    float xOf(LianaId id) {
        var li = lianaById(id).orElseThrow(() ->
            new IllegalArgumentException("Liana no existe: " + id.value()));
        return li.x;
    }

    // Para físicas/jugador (se sigue usando la lista "simple"):
    List<Liana> lianas() {
        List<Liana> list = new ArrayList<>(lianaSlots.size());
        for (var s : lianaSlots) list.add(s.liana);
        return list;
    }
}
