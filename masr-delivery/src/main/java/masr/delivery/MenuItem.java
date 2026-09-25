package masr.delivery;

import java.math.BigDecimal;
import java.util.Map;

public abstract class MenuItem {
    private final ItemKey key;
    private final String name;
    private final String category;
    private final int preparationMinutes;
    private volatile boolean available = true;
    protected MenuItem(ItemKey key, String name, String category, int minutes) {
        this.key = java.util.Objects.requireNonNull(key);
        this.name = Money.text(name, "Item name");
        this.category = Money.text(category, "Category");
        if (minutes < 0) throw new ValidationException("Preparation time cannot be negative.");
        preparationMinutes = minutes;
    }
    public ItemKey key() { return key; }
    public String name() { return name; }
    public String category() { return category; }
    public int preparationMinutes() { return preparationMinutes; }
    public boolean available() { return available; }
    void available(boolean value) { available = value; }
    public abstract BigDecimal unitPrice();
    public String unit() { return "each"; }
    public void validateQuantity(BigDecimal quantity) {
        Money.positive(quantity, "Quantity");
        if (quantity.stripTrailingZeros().scale() > 0) throw new ValidationException("This item requires a whole-number count.");
    }
    public BigDecimal price(BigDecimal quantity) { validateQuantity(quantity); return Money.round(unitPrice().multiply(quantity)); }
    public Map<ItemKey, BigDecimal> stockRequirements(BigDecimal quantity) { validateQuantity(quantity); return Map.of(key, quantity); }
    @Override public final boolean equals(Object obj) { return obj instanceof MenuItem item && key.equals(item.key); }
    @Override public final int hashCode() { return key.hashCode(); }
    @Override public String toString() { return key.itemId() + " - " + name + " | " + unitPrice() + " EGP/" + unit() + (available ? "" : " [unavailable]"); }
}
