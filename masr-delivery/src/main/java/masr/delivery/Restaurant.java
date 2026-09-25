package masr.delivery;

import java.util.*;
public final class Restaurant extends Entity {
    private final String name;
    private final String district;
    private final Set<String> cuisines;
    private final double rating;
    private volatile boolean open;
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();
    private final Set<String> usedIds = new HashSet<>();
    public Restaurant(String id, String name, String district, Collection<String> cuisines, double rating, boolean open) {
        super(id); this.name = Money.text(name, "Restaurant name"); this.district = Money.text(district, "District");
        if (!Double.isFinite(rating) || rating < 0 || rating > 5) throw new ValidationException("Rating must be between 0 and 5.");
        if (cuisines == null || cuisines.isEmpty()) throw new ValidationException("At least one cuisine is required.");
        TreeSet<String> normalized = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        cuisines.forEach(c -> normalized.add(Money.text(c, "Cuisine")));
        this.cuisines = Collections.unmodifiableSet(normalized); this.rating = rating; this.open = open;
    }
    public String name() { return name; }
    public String district() { return district; }
    public Set<String> cuisines() { return cuisines; }
    public double rating() { return rating; }
    public boolean open() { return open; }
    void open(boolean value) { open = value; }
    public synchronized List<MenuItem> menu() { return List.copyOf(menu.values()); }
    public synchronized MenuItem item(String id) {
        MenuItem result = menu.get(id); if (result == null) throw new NotFoundException("Unknown menu item: " + id); return result;
    }
    synchronized void add(MenuItem item) {
        if (!id().equals(item.key().restaurantId())) throw new ValidationException("Wrong restaurant for item.");
        if (usedIds.contains(item.key().itemId())) throw new DuplicateIdException("Menu ID already used: " + item.key());
        if (item instanceof ComboItem combo) for (ComboItem.Part part : combo.parts())
            if (item(part.item().key().itemId()) != part.item()) throw new ValidationException("Use registered combo components.");
        menu.put(item.key().itemId(), item); usedIds.add(item.key().itemId());
    }
    synchronized void remove(String id) {
        MenuItem target = item(id);
        if (menu.values().stream().anyMatch(m -> m instanceof ComboItem c && c.parts().stream().anyMatch(p -> p.item().equals(target))))
            throw new ValidationException("Remove combos that reference this item first.");
        menu.remove(id);
    }
    @Override public String toString() { return id() + " | " + name + " | " + district + " | " + cuisines + " | " + rating; }
}
