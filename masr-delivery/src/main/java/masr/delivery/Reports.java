package masr.delivery;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Reports {
    public record RestaurantRevenue(String id, String name, BigDecimal revenue) { }
    public record RiderStats(String id, String name, long completed, Optional<Duration> averageDuration) { }
    public record PopularItem(ItemKey key, String name, long orderCount) { }
    public record CustomerHistory(List<Order> orders, BigDecimal totalSpent) { public CustomerHistory { orders = List.copyOf(orders); } }
    private final Platform platform;
    public Reports(Platform platform) { this.platform = platform; }
    private boolean completed(Order order) { return order.status() == OrderStatus.DELIVERED; }
    private LocalDate deliveredDate(Order order) { return order.time(OrderStatus.DELIVERED).orElseThrow().atZone(platform.clock().getZone()).toLocalDate(); }
    private BigDecimal sum(List<Order> orders) { return orders.stream().map(o -> o.pricing().total()).reduce(Money.ZERO,BigDecimal::add); }
    public BigDecimal revenue(LocalDate from,LocalDate to) {
        if (to.isBefore(from)) throw new ValidationException("End date precedes start date.");
        synchronized (platform) { return sum(platform.orders().stream().filter(this::completed).filter(o -> !deliveredDate(o).isBefore(from) && !deliveredDate(o).isAfter(to)).toList()); }
    }
    public List<RestaurantRevenue> topRestaurants(YearMonth month) {
        synchronized (platform) {
            return platform.orders().stream().filter(this::completed).filter(o -> YearMonth.from(deliveredDate(o)).equals(month))
                .collect(Collectors.groupingBy(Order::restaurant,Collectors.reducing(Money.ZERO,o -> o.pricing().total(),BigDecimal::add)))
                .entrySet().stream().map(e -> new RestaurantRevenue(e.getKey().id(),e.getKey().name(),e.getValue()))
                .sorted(Comparator.comparing(RestaurantRevenue::revenue).reversed().thenComparing(RestaurantRevenue::name).thenComparing(RestaurantRevenue::id)).limit(5).toList();
        }
    }
    public Map<String,BigDecimal> averagePerDistrict() {
        synchronized (platform) {
            return Collections.unmodifiableMap(platform.orders().stream().filter(this::completed).collect(Collectors.groupingBy(o -> o.address().district().toLowerCase(Locale.ROOT),TreeMap::new,
                Collectors.collectingAndThen(Collectors.toList(),list -> sum(list).divide(BigDecimal.valueOf(list.size()),2,RoundingMode.HALF_UP)))));
        }
    }
    public List<Restaurant> highlyRated() {
        synchronized (platform) {
            Map<Restaurant,Long> count = platform.orders().stream().filter(this::completed).collect(Collectors.groupingBy(Order::restaurant,Collectors.counting()));
            return platform.search(r -> r.rating() > 4.5 && count.getOrDefault(r,0L) >= 20);
        }
    }
    public Map<OrderStatus,Long> statusCounts() {
        synchronized (platform) {
            Map<OrderStatus,Long> counts = platform.orders().stream().collect(Collectors.groupingBy(Order::status,() -> new EnumMap<>(OrderStatus.class),Collectors.counting()));
            return Collections.unmodifiableMap(Arrays.stream(OrderStatus.values()).collect(Collectors.toMap(Function.identity(),s -> counts.getOrDefault(s,0L),(a,b) -> a,() -> new EnumMap<>(OrderStatus.class))));
        }
    }
    public List<RiderStats> riderStats() {
        synchronized (platform) {
            Map<Rider,List<Order>> byRider = platform.orders().stream().filter(this::completed).collect(Collectors.groupingBy(o -> o.rider().orElseThrow()));
            return platform.riders().stream().map(r -> {
                List<Order> delivered = byRider.getOrDefault(r,List.of());
                Optional<Duration> average = delivered.stream().map(o -> Duration.between(o.time(OrderStatus.OUT_FOR_DELIVERY).orElseThrow(),o.time(OrderStatus.DELIVERED).orElseThrow()))
                    .reduce(Duration::plus).map(total -> total.dividedBy(delivered.size()));
                return new RiderStats(r.id(),r.name(),delivered.size(),average);
            }).sorted(Comparator.comparingLong(RiderStats::completed).reversed().thenComparing(RiderStats::id)).toList();
        }
    }
    public Optional<PopularItem> mostPopular() {
        synchronized (platform) {
            return platform.orders().stream().filter(o -> o.status() != OrderStatus.CANCELLED).flatMap(o -> o.lines().stream())
                .collect(Collectors.groupingBy(Order.Line::key)).entrySet().stream()
                .map(e -> new PopularItem(e.getKey(),e.getValue().get(0).name(),e.getValue().size()))
                .sorted(Comparator.comparingLong(PopularItem::orderCount).reversed().thenComparing(p -> p.key().toString())).findFirst();
        }
    }
    public CustomerHistory history(String customerId) {
        synchronized (platform) {
            platform.customer(customerId);
            List<Order> orders = platform.orders().stream().filter(o -> o.customer().id().equals(customerId)).sorted(Comparator.comparing(Order::placedAt).reversed().thenComparing(Order::id,Comparator.reverseOrder())).toList();
            return new CustomerHistory(orders,sum(orders.stream().filter(o -> o.paid() && !o.refunded()).toList()));
        }
    }
    public OptionalInt peakHour() {
        synchronized (platform) {
            Optional<Integer> peak = platform.orders().stream().collect(Collectors.groupingBy(o -> o.placedAt().atZone(platform.clock().getZone()).getHour(),Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.<Integer,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).findFirst();
            return peak.isPresent() ? OptionalInt.of(peak.get()) : OptionalInt.empty();
        }
    }
    public List<Customer> inactiveCustomers() {
        synchronized (platform) {
            Instant cutoff = platform.clock().instant().minus(Duration.ofDays(30));
            Set<Customer> active = platform.orders().stream().filter(o -> !o.placedAt().isBefore(cutoff)).map(Order::customer).collect(Collectors.toSet());
            return platform.customers().stream().filter(c -> !active.contains(c)).sorted(Comparator.comparing(Customer::id)).toList();
        }
    }
}
