package masr.delivery;

import java.math.BigDecimal;
public final class WeightedItem extends MenuItem {
    private final BigDecimal perKg;
    public WeightedItem(ItemKey key, String name, String category, int minutes, BigDecimal price) {
        super(key, name, category, minutes); perKg = Money.positive(Money.round(Money.positive(price, "Price per kg")), "Rounded price");
    }
    @Override public BigDecimal unitPrice() { return perKg; }
    @Override public String unit() { return "kg"; }
    @Override public void validateQuantity(BigDecimal quantity) { Money.positive(quantity, "Weight"); }
}
