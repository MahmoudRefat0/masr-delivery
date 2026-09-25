package masr.delivery;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
public final class MenuItemFactory {
    public record Spec(Restaurant restaurant, String id, String name, String category, int minutes, String value, String parts) {
        ItemKey key() { return new ItemKey(restaurant.id(), id); }
    }
    private final Map<String, Function<Spec, MenuItem>> creators = new HashMap<>();
    public MenuItemFactory() {
        register("standard", s -> new StandardItem(s.key(),s.name(),s.category(),s.minutes(),new BigDecimal(s.value())));
        register("weighted", s -> new WeightedItem(s.key(),s.name(),s.category(),s.minutes(),new BigDecimal(s.value())));
        register("combo", s -> {
            List<ComboItem.Part> parts = Arrays.stream(s.parts().split(",")).map(text -> {
                String[] pair = text.split(":");
                if (pair.length != 2) throw new ValidationException("Combo parts use itemId:quantity.");
                return new ComboItem.Part(s.restaurant().item(pair[0]),new BigDecimal(pair[1]));
            }).toList();
            return new ComboItem(s.key(),s.name(),s.category(),s.minutes(),parts,new BigDecimal(s.value()));
        });
    }
    public void register(String type, Function<Spec, MenuItem> creator) {
        if (creators.putIfAbsent(Money.text(type,"Type").toLowerCase(Locale.ROOT),Objects.requireNonNull(creator)) != null) throw new DuplicateIdException("Item type already registered.");
    }
    public MenuItem create(String type, Spec spec) {
        Function<Spec,MenuItem> creator = creators.get(type.toLowerCase(Locale.ROOT));
        if (creator == null) throw new ValidationException("Unknown item type: " + type);
        return creator.apply(spec);
    }
}
