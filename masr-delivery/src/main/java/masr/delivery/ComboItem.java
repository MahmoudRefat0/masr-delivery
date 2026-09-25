package masr.delivery;

import java.math.BigDecimal;
import java.util.*;
public final class ComboItem extends MenuItem {
    public record Part(MenuItem item, BigDecimal quantity) {
        public Part { Objects.requireNonNull(item); item.validateQuantity(quantity); }
    }
    private final List<Part> parts;
    private final BigDecimal discount;
    public ComboItem(ItemKey key, String name, String category, int minutes, List<Part> parts, BigDecimal discount) {
        super(key, name, category, minutes);
        if (parts == null || parts.isEmpty()) throw new ValidationException("A combo needs components.");
        this.parts = List.copyOf(parts);
        if (parts.stream().anyMatch(p -> !p.item().key().restaurantId().equals(key.restaurantId()))) throw new ValidationException("Combo components must belong to the same restaurant.");
        if (discount == null || discount.signum() < 0 || discount.compareTo(BigDecimal.ONE) >= 0) throw new ValidationException("Combo discount must be from 0 to less than 1.");
        this.discount = discount;
        Money.positive(unitPrice(), "Combo price");
    }
    public List<Part> parts() { return parts; }
    @Override public BigDecimal unitPrice() {
        return Money.round(parts.stream().map(p -> p.item().price(p.quantity())).reduce(Money.ZERO, BigDecimal::add).multiply(BigDecimal.ONE.subtract(discount)));
    }
    @Override public Map<ItemKey, BigDecimal> stockRequirements(BigDecimal quantity) {
        validateQuantity(quantity);
        Map<ItemKey, BigDecimal> result = new LinkedHashMap<>();
        for (Part part : parts) part.item().stockRequirements(part.quantity().multiply(quantity)).forEach((key, value) -> result.merge(key, value, BigDecimal::add));
        return Map.copyOf(result);
    }
}
