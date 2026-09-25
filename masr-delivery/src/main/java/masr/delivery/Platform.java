package masr.delivery;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The transaction boundary for stock, wallets and dispatch. */
public final class Platform {
    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final Map<String, Rider> riders = new LinkedHashMap<>();
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, Promotion> promotions = new HashMap<>();
    private final Map<ItemKey, BigDecimal> stock = new HashMap<>();
    private final Set<String> removedRestaurants = new HashSet<>();
    private final PriorityQueue<Order> ready = new PriorityQueue<>(Comparator
            .comparingInt((Order o) -> o.tierAtPlacement() == LoyaltyTier.GOLD ? 0 : 1)
            .thenComparing(o -> o.time(OrderStatus.READY).orElseThrow()).thenComparing(Order::id));
    private final Clock clock;
    private final DistanceTable distances;
    private final Pricing pricing = new Pricing(AppConfig.get());
    private final OrderEvents events = new OrderEvents();
    private long sequence;
    public Platform(Clock clock, DistanceTable distances) { this.clock = Objects.requireNonNull(clock); this.distances = Objects.requireNonNull(distances); }
    public Clock clock() { return clock; }
    public OrderEvents events() { return events; }
    private static <T> T get(Map<String,T> map, String id) { T value = map.get(id); if (value == null) throw new NotFoundException("Unknown identifier: " + id); return value; }
    private static <T> void add(Map<String,T> map, String id, T value) { if (map.putIfAbsent(id,value) != null) throw new DuplicateIdException("Duplicate identifier: " + id); }
    public synchronized void addRestaurant(Restaurant value) { if (removedRestaurants.contains(value.id())) throw new DuplicateIdException("Restaurant ID has already been used."); add(restaurants,value.id(),value); }
    public synchronized void addCustomer(Customer value) { add(customers,value.id(),value); }
    public synchronized void addRider(Rider value) { add(riders,value.id(),value); }
    public synchronized void addPromotion(Promotion value) { add(promotions,value.code(),value); }
    public synchronized Restaurant restaurant(String id) { return get(restaurants,id); }
    public synchronized Customer customer(String id) { return get(customers,id); }
    public synchronized Rider rider(String id) { return get(riders,id); }
    public synchronized Order order(String id) { return get(orders,id); }
    public synchronized List<Restaurant> restaurants() { return List.copyOf(restaurants.values()); }
    public synchronized List<Customer> customers() { return List.copyOf(customers.values()); }
    public synchronized List<Rider> riders() { return List.copyOf(riders.values()); }
    public synchronized List<Order> orders() { return List.copyOf(orders.values()); }
    public synchronized List<Order> dispatchQueue() { return ready.stream().sorted(ready.comparator()).toList(); }
    public synchronized Set<String> cuisines() {
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        restaurants.values().forEach(r -> result.addAll(r.cuisines())); return Collections.unmodifiableSet(result);
    }
    public synchronized List<Restaurant> search(Predicate<Restaurant> condition) {
        return restaurants.values().stream().filter(condition)
                .sorted(Comparator.comparingDouble(Restaurant::rating).reversed().thenComparing(Restaurant::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Restaurant::id)).toList();
    }
    public synchronized List<Restaurant> searchText(String customerId, String text) {
        String term = Money.text(text,"Search"); customer(customerId).search(term);
        String lower = term.toLowerCase(Locale.ROOT);
        return search(r -> r.open() && (r.name().toLowerCase(Locale.ROOT).contains(lower) || r.cuisines().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(lower))));
    }
    public synchronized void setOpen(String restaurantId, boolean open) { restaurant(restaurantId).open(open); }
    public synchronized void addItem(String restaurantId, MenuItem item, BigDecimal initialStock) {
        validateStock(initialStock);
        if (initialStock.signum() > 0) item.validateQuantity(initialStock);
        restaurant(restaurantId).add(item); stock.put(item.key(),initialStock);
    }
    private void validateStock(BigDecimal amount) { if (amount == null || amount.signum() < 0) throw new ValidationException("Stock cannot be negative."); }
    public synchronized void setStock(String restaurantId, String itemId, BigDecimal amount) {
        validateStock(amount); MenuItem item = restaurant(restaurantId).item(itemId);
        if (item instanceof ComboItem) throw new ValidationException("Adjust component stock for a combo.");
        if (amount.signum() > 0) item.validateQuantity(amount);
        stock.put(item.key(),amount);
    }
    public synchronized BigDecimal stock(ItemKey key) { return stock.getOrDefault(key, BigDecimal.ZERO); }
    public synchronized void setAvailable(String restaurantId, String itemId, boolean value) { restaurant(restaurantId).item(itemId).available(value); }
    public synchronized void removeItem(String restaurantId, String itemId) { restaurant(restaurantId).remove(itemId); }
    public synchronized void removeRestaurant(String id) {
        restaurant(id);
        if (orders.values().stream().anyMatch(o -> o.restaurant().id().equals(id) && !o.status().terminal())) throw new ValidationException("Restaurant has active orders.");
        restaurants.remove(id); removedRestaurants.add(id);
    }
    private void checkAvailable(MenuItem item) {
        if (!item.available()) throw new ItemUnavailableException("Unavailable item: " + item.name());
        if (item instanceof ComboItem combo) combo.parts().forEach(p -> checkAvailable(p.item()));
    }
    public synchronized Order place(Order.Request request) {
        if (request == null || request.customer() == null || request.restaurant() == null || request.address() == null || request.quantities().isEmpty()) throw new ValidationException("Incomplete order.");
        Customer customer = customer(request.customer().id()); Restaurant restaurant = restaurant(request.restaurant().id());
        if (customer != request.customer() || restaurant != request.restaurant()) throw new ValidationException("Use the registered customer and restaurant.");
        if (!restaurant.open()) throw new RestaurantClosedException(restaurant.name() + " is closed.");
        if (!customer.addresses().contains(request.address())) throw new ValidationException("Address does not belong to this customer.");
        List<Order.Line> lines = new ArrayList<>(); Map<ItemKey,BigDecimal> reservation = new LinkedHashMap<>();
        for (Map.Entry<String,BigDecimal> entry : request.quantities().entrySet()) {
            MenuItem item = restaurant.item(entry.getKey()); checkAvailable(item);
            BigDecimal amount = item.price(entry.getValue());
            lines.add(new Order.Line(item.key(),item.name(),entry.getValue(),amount));
            item.stockRequirements(entry.getValue()).forEach((key,value) -> reservation.merge(key,value,BigDecimal::add));
        }
        reservation.forEach((key,quantity) -> { if (stock(key).compareTo(quantity) < 0) throw new StockShortageException("Insufficient stock for " + key + "; available " + stock(key) + ", requested " + quantity + "."); });
        BigDecimal km = distances.km(restaurant.district(),request.address().district());
        boolean first = orders.values().stream().noneMatch(o -> o.customer().equals(customer) && o.status() != OrderStatus.CANCELLED);
        Promotion promo = null;
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            promo = promotions.get(request.promoCode().trim().toUpperCase(Locale.ROOT));
            if (promo == null) throw new InvalidPromotionException("Unknown promotion code.");
        }
        Pricing.Breakdown breakdown = pricing.calculate(lines,km,customer.tier(),request.address(),first,LocalDate.now(clock),promo);
        BigDecimal load = reservation.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        Order order = new Order(String.format(Locale.ROOT,"O%06d",++sequence),request,lines,reservation,breakdown,km,load,clock.instant());
        reservation.forEach((key,quantity) -> stock.put(key,stock(key).subtract(quantity)));
        orders.put(order.id(),order); publish(order,null); return order;
    }
    public synchronized void pay(String customerId, String orderId) {
        Order order = owned(customerId,orderId);
        if (order.status() == OrderStatus.CANCELLED) throw new ValidationException("Cancelled orders cannot be paid.");
        if (order.paid()) throw new ValidationException("Order has already been paid.");
        order.customer().debit(order.pricing().total()); order.markPaid();
    }
    private Order owned(String customerId, String orderId) {
        customer(customerId); Order order = order(orderId);
        if (!order.customer().id().equals(customerId)) throw new ValidationException("This order belongs to another customer."); return order;
    }
    public synchronized Order track(String customerId,String orderId) { return owned(customerId,orderId); }
    public synchronized void cancel(String customerId,String orderId) { cancelOrder(owned(customerId,orderId)); }
    private void cancelOrder(Order order) {
        if (!order.status().canMoveTo(OrderStatus.CANCELLED)) throw new IllegalTransitionException("Order cannot be cancelled after pickup or after it has ended.");
        OrderStatus before = order.status(); order.move(OrderStatus.CANCELLED,clock.instant()); ready.remove(order);
        order.reservation().forEach((key,quantity) -> stock.put(key,stock(key).add(quantity)));
        if (order.paid()) { order.customer().credit(order.pricing().total()); order.markRefunded(); }
        order.rider().ifPresent(r -> r.release(false,order.address().district()));
        publish(order,before); dispatch();
    }
    public synchronized void restaurantStatus(String restaurantId,String orderId,OrderStatus next) {
        restaurant(restaurantId); Order order = order(orderId);
        if (!order.restaurant().id().equals(restaurantId)) throw new ValidationException("Order belongs to another restaurant.");
        if (next == OrderStatus.CANCELLED) {
            if (order.status() != OrderStatus.PLACED) throw new IllegalTransitionException("Only a pending order can be rejected by the restaurant.");
            cancelOrder(order); return;
        }
        if (next != OrderStatus.ACCEPTED && next != OrderStatus.PREPARING && next != OrderStatus.READY) throw new IllegalTransitionException("Not a restaurant transition.");
        if (!order.paid()) throw new ValidationException("Customer must pay before acceptance.");
        OrderStatus before = order.status(); order.move(next,clock.instant());
        if (next == OrderStatus.READY) ready.add(order);
        publish(order,before); if (next == OrderStatus.READY) dispatch();
    }
    public synchronized void duty(String riderId,boolean value) { rider(riderId).duty(value); if (value) dispatch(); }
    public synchronized void assign(String orderId,String riderId) {
        Order order = order(orderId); Rider rider = rider(riderId);
        if (order.status() != OrderStatus.READY) throw new IllegalTransitionException("Only READY orders can be assigned.");
        if (!rider.available()) throw new RiderBusyException("Rider is already assigned or off duty.");
        if (!eligible(rider,order)) throw new ValidationException("Rider district, range or capacity does not fit this order.");
        Order firstEligible = dispatchQueue().stream().filter(o -> eligible(rider,o)).findFirst().orElseThrow();
        if (firstEligible != order) throw new ValidationException("A higher-priority eligible order is waiting.");
        rider.assign(order.id()); order.rider(rider); order.move(OrderStatus.ASSIGNED,clock.instant()); ready.remove(order); publish(order,OrderStatus.READY);
    }
    private boolean eligible(Rider rider,Order order) { return rider.district().equalsIgnoreCase(order.restaurant().district()) && rider.policy().eligible(order.kilometres(),order.load()); }
    public synchronized void dispatch() {
        for (Order order : dispatchQueue()) {
            Optional<Rider> candidate = riders.values().stream().filter(Rider::available).filter(r -> eligible(r,order))
                    .min(Comparator.comparingInt((Rider r) -> r.policy().estimatedMinutes(order.kilometres())).thenComparing(Rider::id));
            candidate.ifPresent(r -> assign(order.id(),r.id()));
        }
    }
    public synchronized void relocate(String riderId,String district) {
        Rider rider = rider(riderId); if (rider.activeOrder().isPresent()) throw new RiderBusyException("Cannot relocate with an active order.");
        rider.relocate(district); dispatch();
    }
    public synchronized void pickup(String riderId) {
        Rider rider = rider(riderId); Order order = order(rider.activeOrder().orElseThrow(() -> new NotFoundException("No active order.")));
        OrderStatus before = order.status(); order.move(OrderStatus.OUT_FOR_DELIVERY,clock.instant()); publish(order,before);
    }
    public synchronized void deliver(String riderId) {
        Rider rider = rider(riderId); Order order = order(rider.activeOrder().orElseThrow(() -> new NotFoundException("No active order.")));
        OrderStatus before = order.status(); order.move(OrderStatus.DELIVERED,clock.instant());
        rider.release(true,order.address().district()); order.customer().completed(); publish(order,before); dispatch();
    }
    private void publish(Order order,OrderStatus before) {
        events.publish(new OrderEvents.Event(order.id(),order.customer().id(),order.rider().map(Rider::id).orElse(null),before,order.status(),clock.instant()));
    }
}
