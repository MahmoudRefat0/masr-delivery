package masr.delivery;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class Order extends Entity {
    public record Line(ItemKey key, String name, BigDecimal quantity, BigDecimal amount) { }
    public record Request(Customer customer, Restaurant restaurant, Address address, Map<String, BigDecimal> quantities, String promoCode, String notes) {
        public Request { quantities = Collections.unmodifiableMap(new LinkedHashMap<>(quantities)); }
    }
    public static final class Builder {
        private Customer customer;
        private Restaurant restaurant;
        private Address address;
        private final Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        private String promo;
        private String notes = "";
        public Builder customer(Customer value) { customer = value; return this; }
        public Builder restaurant(Restaurant value) { restaurant = value; return this; }
        public Builder address(Address value) { address = value; return this; }
        public Builder item(String id, BigDecimal quantity) { quantities.merge(Money.text(id,"Item ID"), Money.positive(quantity,"Quantity"), BigDecimal::add); return this; }
        public Builder promotion(String value) { promo = value; return this; }
        public Builder notes(String value) { notes = value == null ? "" : value; return this; }
        public Request build() {
            if (customer == null || restaurant == null || address == null || quantities.isEmpty()) throw new ValidationException("Customer, restaurant, address and at least one item are required.");
            return new Request(customer, restaurant, address, quantities, promo, notes);
        }
    }
    private final Customer customer;
    private final Restaurant restaurant;
    private final Address address;
    private final List<Line> lines;
    private final Map<ItemKey, BigDecimal> reservation;
    private final Pricing.Breakdown pricing;
    private final BigDecimal kilometres;
    private final BigDecimal load;
    private final LoyaltyTier tierAtPlacement;
    private final String notes;
    private final String promoCode;
    private final Instant placedAt;
    private final EnumMap<OrderStatus, Instant> timestamps = new EnumMap<>(OrderStatus.class);
    private volatile OrderStatus status = OrderStatus.PLACED;
    private volatile boolean paid;
    private volatile boolean refunded;
    private volatile Rider rider;
    Order(String id, Request request, List<Line> lines, Map<ItemKey, BigDecimal> reservation, Pricing.Breakdown pricing, BigDecimal km, BigDecimal load, Instant now) {
        super(id); customer = request.customer(); restaurant = request.restaurant(); address = request.address();
        this.lines = List.copyOf(lines); this.reservation = Map.copyOf(reservation); this.pricing = pricing; kilometres = km; this.load = load;
        notes = request.notes(); promoCode = request.promoCode(); tierAtPlacement = customer.tier(); placedAt = now; timestamps.put(status, now);
    }
    public Customer customer() { return customer; }
    public Restaurant restaurant() { return restaurant; }
    public Address address() { return address; }
    public List<Line> lines() { return lines; }
    public Pricing.Breakdown pricing() { return pricing; }
    public BigDecimal kilometres() { return kilometres; }
    public BigDecimal load() { return load; }
    public LoyaltyTier tierAtPlacement() { return tierAtPlacement; }
    public String notes() { return notes; }
    public Optional<String> promoCode() { return Optional.ofNullable(promoCode); }
    public Instant placedAt() { return placedAt; }
    public OrderStatus status() { return status; }
    public boolean paid() { return paid; }
    public boolean refunded() { return refunded; }
    public Optional<Rider> rider() { return Optional.ofNullable(rider); }
    public synchronized Optional<Instant> time(OrderStatus state) { return Optional.ofNullable(timestamps.get(state)); }
    public synchronized Map<OrderStatus, Instant> timeline() { return Collections.unmodifiableMap(new EnumMap<>(timestamps)); }
    Map<ItemKey, BigDecimal> reservation() { return reservation; }
    void markPaid() { paid = true; }
    void markRefunded() { refunded = true; }
    void rider(Rider value) { rider = value; }
    synchronized void move(OrderStatus next, Instant now) {
        if (!status.canMoveTo(next)) throw new IllegalTransitionException("Cannot move " + id() + " from " + status + " to " + next + ".");
        status = next; timestamps.put(next, now);
    }
    @Override public String toString() { return id() + " | " + restaurant.name() + " | " + status + " | " + pricing.total() + " EGP | " + (paid ? (refunded ? "refunded" : "paid") : "unpaid"); }
}
