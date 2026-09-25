package masr.delivery;

public record ItemKey(String restaurantId, String itemId) {
    public ItemKey { restaurantId = Money.text(restaurantId, "Restaurant ID"); itemId = Money.text(itemId, "Item ID"); }
    @Override public String toString() { return restaurantId + "/" + itemId; }
}
