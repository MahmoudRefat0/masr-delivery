package masr.delivery;

import java.math.BigDecimal;
public final class StandardItem extends MenuItem {
    private final BigDecimal price;
    public StandardItem(ItemKey key, String name, String category, int minutes, BigDecimal price) {
        super(key, name, category, minutes); this.price = Money.positive(Money.round(Money.positive(price, "Price")), "Rounded price");
    }
    @Override public BigDecimal unitPrice() { return price; }
}
