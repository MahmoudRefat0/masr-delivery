package masr.delivery;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free regression suite; run with ./test.sh or test.bat. */
public final class PlatformTest {
    private static int passed;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final Address ADDRESS = new Address("Faisal","12 Test Street");
    private static final class TestClock extends Clock {
        private Instant instant = Instant.parse("2026-01-15T10:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("Africa/Cairo"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant,zone); }
        @Override public Instant instant() { return instant; }
    }
    private record Fixture(Platform p, TestClock clock, Customer c, Restaurant r, Rider d) { }
    private static Fixture fixture() {
        TestClock clock = new TestClock(); Platform p = new Platform(clock,DistanceTable.demo());
        Customer c = new Customer("C1","Test Customer","01012345678",List.of(ADDRESS),new BigDecimal("1000000"));
        Restaurant r = new Restaurant("R1","Test Restaurant","Maadi",List.of("Egyptian","Grill"),4.8,true);
        Rider d = new Rider("D1","Test Rider",VehiclePolicy.motorcycle(),"Maadi");
        p.addCustomer(c); p.addRestaurant(r); p.addRider(d);
        p.addItem("R1",new StandardItem(new ItemKey("R1","M1"),"Chicken","Mains",20,new BigDecimal("120")),new BigDecimal("1000"));
        p.addItem("R1",new StandardItem(new ItemKey("R1","M2"),"Rice","Sides",10,new BigDecimal("35")),new BigDecimal("1000"));
        p.addItem("R1",new WeightedItem(new ItemKey("R1","M3"),"Kofta","Grill",25,new BigDecimal("400")),new BigDecimal("100"));
        p.addItem("R1",new ComboItem(new ItemKey("R1","M4"),"Meal","Combos",20,List.of(new ComboItem.Part(r.item("M1"),ONE),new ComboItem.Part(r.item("M2"),ONE)),new BigDecimal("0.10")),BigDecimal.ZERO);
        p.addPromotion(new Promotion("NILE20",Promotion.percentage(new BigDecimal(".20"),new BigDecimal("50")),List.of()));
        return new Fixture(p,clock,c,r,d);
    }
    private static Order.Builder builder(Fixture f) { return new Order.Builder().customer(f.c).restaurant(f.r).address(ADDRESS); }
    private static Order place(Fixture f) { return f.p.place(builder(f).item("M1",ONE).build()); }
    private static void ready(Fixture f,Order o) {
        f.p.pay(o.customer().id(),o.id());
        f.p.restaurantStatus("R1",o.id(),OrderStatus.ACCEPTED);
        f.p.restaurantStatus("R1",o.id(),OrderStatus.PREPARING);
        f.p.restaurantStatus("R1",o.id(),OrderStatus.READY);
    }
    private static void complete(Fixture f,Order o) {
        ready(f,o); f.p.relocate("D1","Maadi"); f.p.duty("D1",true); f.p.pickup("D1"); f.clock.advance(Duration.ofMinutes(10)); f.p.deliver("D1");
    }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static void money(String expected,BigDecimal actual) { check(new BigDecimal(expected).compareTo(actual) == 0,"Expected " + expected + ", got " + actual); }
    private static void rejects(Class<? extends Throwable> type,Runnable action) {
        try { action.run(); } catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError("Wrong exception: " + error,error); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static void test(String name,Runnable action) { action.run(); passed++; System.out.println("PASS " + name); }
    private static void race(Runnable first,Runnable second) {
        ExecutorService pool = Executors.newFixedThreadPool(AppConfig.get().poolSize());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = List.of(pool.submit(() -> { await(start); first.run(); }),pool.submit(() -> { await(start); second.run(); }));
            start.countDown(); for (Future<?> task : tasks) task.get(5,TimeUnit.SECONDS);
        } catch (Exception e) { throw new AssertionError(e); }
        finally { pool.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) { try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    public static void main(String[] args) {
        test("exact assignment example",() -> { Fixture f=fixture(); money("258.00",f.p.place(builder(f).item("M1",new BigDecimal("2")).promotion("nIlE20").build()).pricing().total()); });
        test("weighted price and fractional inventory",() -> { Fixture f=fixture(); Order o=f.p.place(builder(f).item("M3",new BigDecimal(".375")).build()); money("150",o.pricing().subtotal()); money("99.625",f.p.stock(new ItemKey("R1","M3"))); });
        test("combo uses discounted components and reserves stock",() -> { Fixture f=fixture(); Order o=f.p.place(builder(f).item("M4",new BigDecimal("2")).build()); money("279",o.pricing().subtotal()); money("998",f.p.stock(new ItemKey("R1","M1"))); money("998",f.p.stock(new ItemKey("R1","M2"))); });
        test("aggregate combo and standalone stock shortage is atomic",() -> { Fixture f=fixture(); f.p.setStock("R1","M1",ONE); rejects(StockShortageException.class,() -> f.p.place(builder(f).item("M4",ONE).item("M1",ONE).build())); money("1",f.p.stock(new ItemKey("R1","M1"))); money("1000",f.p.stock(new ItemKey("R1","M2"))); check(f.p.orders().isEmpty(),"No partial order"); });
        test("unavailable combo component rejected",() -> { Fixture f=fixture(); f.p.setAvailable("R1","M2",false); rejects(ItemUnavailableException.class,() -> f.p.place(builder(f).item("M4",ONE).build())); });
        test("fractional standard count rejected",() -> { Fixture f=fixture(); rejects(ValidationException.class,() -> f.p.place(builder(f).item("M1",new BigDecimal(".5")).build())); });
        test("closed restaurant rejected",() -> { Fixture f=fixture(); f.p.setOpen("R1",false); rejects(RestaurantClosedException.class,() -> place(f)); });
        test("zero and negative quantities rejected",() -> { Fixture f=fixture(); rejects(ValidationException.class,() -> builder(f).item("M1",BigDecimal.ZERO)); rejects(ValidationException.class,() -> builder(f).item("M3",new BigDecimal("-1"))); });
        test("empty order and foreign address rejected",() -> { Fixture f=fixture(); rejects(ValidationException.class,() -> builder(f).build()); rejects(ValidationException.class,() -> f.p.place(builder(f).address(new Address("Dokki","Other")).item("M1",ONE).build())); });
        test("invalid mobile rating and price rejected",() -> {
            rejects(ValidationException.class,() -> new Customer("x","x","01312345678",List.of(ADDRESS),ONE));
            rejects(ValidationException.class,() -> new Restaurant("x","x","Maadi",List.of("x"),Double.NaN,true));
            rejects(ValidationException.class,() -> new StandardItem(new ItemKey("x","x"),"x","x",1,BigDecimal.ZERO));
        });
        test("promo cap",() -> { Fixture f=fixture(); money("50",f.p.place(builder(f).item("M1",new BigDecimal("3")).promotion("NILE20").build()).pricing().discount()); });
        test("fixed discount cannot consume fees",() -> { Fixture f=fixture(); f.p.addPromotion(new Promotion("BIG",Promotion.fixed(new BigDecimal("10000")),List.of())); Order o=f.p.place(builder(f).item("M1",ONE).promotion("BIG").build()); money("120",o.pricing().discount()); money("54",o.pricing().total()); });
        test("free delivery preserves service fee",() -> { Fixture f=fixture(); f.p.addPromotion(new Promotion("FREE",Promotion.freeDelivery(),List.of())); Order o=f.p.place(builder(f).item("M1",ONE).promotion("FREE").build()); money("0",o.pricing().deliveryFee()); money("12",o.pricing().serviceFee()); money("132",o.pricing().total()); });
        test("expired promo rolls back placement",() -> { Fixture f=fixture(); f.p.addPromotion(new Promotion("OLD",Promotion.fixed(ONE),List.of(Promotion.expires(LocalDate.of(2026,1,14))))); rejects(InvalidPromotionException.class,() -> f.p.place(builder(f).item("M1",ONE).promotion("OLD").build())); money("1000",f.p.stock(new ItemKey("R1","M1"))); check(f.p.orders().isEmpty(),"No order"); });
        test("expiry date inclusive",() -> { Fixture f=fixture(); f.p.addPromotion(new Promotion("TODAY",Promotion.fixed(ONE),List.of(Promotion.expires(LocalDate.of(2026,1,15))))); money("1",f.p.place(builder(f).item("M1",ONE).promotion("TODAY").build()).pricing().discount()); });
        test("minimum district and unknown code rejected",() -> { Fixture f=fixture();
            f.p.addPromotion(new Promotion("MIN",Promotion.fixed(ONE),List.of(Promotion.minimum(new BigDecimal("200")))));
            f.p.addPromotion(new Promotion("AREA",Promotion.fixed(ONE),List.of(Promotion.district("Dokki"))));
            for (String code:List.of("MIN","AREA","UNKNOWN")) rejects(InvalidPromotionException.class,() -> f.p.place(builder(f).item("M1",ONE).promotion(code).build()));
        });
        test("first-time restriction includes pending orders",() -> { Fixture f=fixture(); f.p.addPromotion(new Promotion("NEW",Promotion.fixed(ONE),List.of(Promotion.firstTime()))); Order o=f.p.place(builder(f).item("M1",ONE).promotion("NEW").build()); rejects(InvalidPromotionException.class,() -> f.p.place(builder(f).item("M1",ONE).promotion("NEW").build())); f.p.cancel("C1",o.id()); f.p.place(builder(f).item("M1",ONE).promotion("NEW").build()); });
        test("piastre rounding uses decimal HALF_UP",() -> { Fixture f=fixture(); f.p.addItem("R1",new StandardItem(new ItemKey("R1","P"),"Tiny","Test",0,new BigDecimal(".05")),ONE); money(".01",f.p.place(builder(f).item("P",ONE).build()).pricing().serviceFee()); });
        test("insufficient wallet does not deduct",() -> { Fixture f=fixture(); Customer poor=new Customer("C2","Poor","01512345678",List.of(ADDRESS),ONE); f.p.addCustomer(poor); Order o=f.p.place(builder(f).customer(poor).item("M1",ONE).build()); rejects(InsufficientFundsException.class,() -> f.p.pay("C2",o.id())); money("1",poor.wallet()); check(!o.paid(),"Unpaid"); });
        test("double payment rejected",() -> { Fixture f=fixture(); Order o=place(f); f.p.pay("C1",o.id()); BigDecimal balance=f.c.wallet(); rejects(ValidationException.class,() -> f.p.pay("C1",o.id())); money(balance.toString(),f.c.wallet()); });
        test("cancel paid order refunds exactly once",() -> { Fixture f=fixture(); Order o=place(f); f.p.pay("C1",o.id()); f.p.cancel("C1",o.id()); money("1000000",f.c.wallet()); money("1000",f.p.stock(new ItemKey("R1","M1"))); check(o.refunded(),"Refund marked"); rejects(IllegalTransitionException.class,() -> f.p.cancel("C1",o.id())); });
        test("unpaid rejection restores stock without refund",() -> { Fixture f=fixture(); Order o=place(f); f.p.restaurantStatus("R1",o.id(),OrderStatus.CANCELLED); money("1000000",f.c.wallet()); check(!o.refunded(),"No refund owed"); });
        test("illegal lifecycle jumps and unpaid acceptance",() -> { Fixture f=fixture(); Order o=place(f); rejects(ValidationException.class,() -> f.p.restaurantStatus("R1",o.id(),OrderStatus.ACCEPTED)); f.p.pay("C1",o.id()); rejects(IllegalTransitionException.class,() -> f.p.restaurantStatus("R1",o.id(),OrderStatus.READY)); rejects(IllegalTransitionException.class,() -> f.p.restaurantStatus("R1",o.id(),OrderStatus.DELIVERED)); });
        test("complete lifecycle and no cancellation after pickup",() -> { Fixture f=fixture(); Order o=place(f); ready(f,o); f.p.duty("D1",true); check(o.status()==OrderStatus.ASSIGNED,"Assigned"); f.p.pickup("D1"); rejects(IllegalTransitionException.class,() -> f.p.cancel("C1",o.id())); f.clock.advance(Duration.ofMinutes(12)); f.p.deliver("D1"); check(f.c.completedOrders()==1 && f.d.completedDeliveries()==1,"Completion counters"); check(o.timeline().size()==7,"All transition timestamps"); rejects(IllegalTransitionException.class,() -> f.p.cancel("C1",o.id())); });
        test("assigned cancellation releases rider",() -> { Fixture f=fixture(); Order o=place(f); ready(f,o); f.p.duty("D1",true); f.p.cancel("C1",o.id()); check(f.d.activeOrder().isEmpty() && f.d.available(),"Released"); });
        test("rider cannot go off duty with active order",() -> { Fixture f=fixture(); Order o=place(f); ready(f,o); f.p.duty("D1",true); rejects(RiderBusyException.class,() -> f.p.duty("D1",false)); });
        test("delivery base and loyalty thresholds",() -> {
            check(LoyaltyTier.from(9)==LoyaltyTier.BRONZE && LoyaltyTier.from(10)==LoyaltyTier.SILVER && LoyaltyTier.from(29)==LoyaltyTier.SILVER && LoyaltyTier.from(30)==LoyaltyTier.GOLD,"Tier boundaries");
            Fixture f=fixture(); for(int i=0;i<10;i++) complete(f,place(f)); check(f.c.tier()==LoyaltyTier.SILVER,"Derived silver"); money("37.80",place(f).pricing().deliveryFee());
            Customer local=new Customer("L","Local","01212345678",List.of(new Address("Maadi","Road 9")),new BigDecimal("1000")); f.p.addCustomer(local);
            money("15",f.p.place(builder(f).customer(local).address(local.addresses().iterator().next()).item("M1",ONE).build()).pricing().deliveryFee());
        });
        test("gold priority overrides older bronze READY order",() -> { Fixture f=fixture(); for(int i=0;i<30;i++) complete(f,place(f)); check(f.c.tier()==LoyaltyTier.GOLD,"Derived gold"); f.p.duty("D1",false);
            Customer bronze=new Customer("B","Bronze","01112345678",List.of(ADDRESS),new BigDecimal("1000")); f.p.addCustomer(bronze);
            Order older=f.p.place(builder(f).customer(bronze).item("M1",ONE).build()); ready(f,older); f.clock.advance(Duration.ofSeconds(5)); Order gold=place(f); ready(f,gold);
            money("0",gold.pricing().deliveryFee()); check(f.p.dispatchQueue().get(0)==gold,"Gold first"); f.p.relocate("D1","Maadi"); f.p.duty("D1",true); check(f.d.activeOrder().orElseThrow().equals(gold.id()),"Gold assigned");
        });
        test("FIFO follows READY time, not placement",() -> { Fixture f=fixture(); Order first=place(f),second=place(f); ready(f,second); f.clock.advance(Duration.ofSeconds(1)); ready(f,first); f.p.duty("D1",true); check(f.d.activeOrder().orElseThrow().equals(second.id()),"Longest ready wait first"); });
        test("vehicle range and district constrain dispatch",() -> { Fixture f=fixture(); f.p.addRider(new Rider("BIKE","Bike",VehiclePolicy.bicycle(),"Maadi")); Order o=place(f); ready(f,o); f.p.duty("BIKE",true); check(o.status()==OrderStatus.READY,"Out of bike range"); f.p.relocate("D1","Dokki"); f.p.duty("D1",true); check(o.status()==OrderStatus.READY,"Wrong district"); });
        test("concurrent dispatch never double-books rider",() -> {
            for(int i=0;i<100;i++) { Fixture f=fixture(); Order a=place(f),b=place(f); ready(f,a); ready(f,b); race(() -> f.p.duty("D1",true),() -> f.p.duty("D1",true)); check(f.p.orders().stream().filter(o -> o.status()==OrderStatus.ASSIGNED).count()==1,"Exactly one assigned"); check(f.p.dispatchQueue().size()==1,"One still waiting"); rejects(RiderBusyException.class,() -> f.p.assign(b.id(),"D1")); }
        });
        test("concurrent final-stock reservations",() -> { Fixture f=fixture(); f.p.setStock("R1","M1",ONE); AtomicInteger won=new AtomicInteger(),lost=new AtomicInteger(); Runnable action=() -> { try { place(f); won.incrementAndGet(); } catch(StockShortageException expected) { lost.incrementAndGet(); } }; race(action,action); check(won.get()==1 && lost.get()==1,"One order wins"); money("0",f.p.stock(new ItemKey("R1","M1"))); });
        test("concurrent payment deducts once",() -> { Fixture f=fixture(); Order o=place(f); AtomicInteger paid=new AtomicInteger(); Runnable action=() -> { try { f.p.pay("C1",o.id()); paid.incrementAndGet(); } catch(ValidationException expected) { } }; race(action,action); check(paid.get()==1,"One payment"); money("999826",f.c.wallet()); });
        test("concurrent cancellation refunds once",() -> { Fixture f=fixture(); Order o=place(f); f.p.pay("C1",o.id()); AtomicInteger cancelled=new AtomicInteger(); Runnable action=() -> { try { f.p.cancel("C1",o.id()); cancelled.incrementAndGet(); } catch(IllegalTransitionException expected) { } }; race(action,action); check(cancelled.get()==1,"One cancellation"); money("1000000",f.c.wallet()); money("1000",f.p.stock(new ItemKey("R1","M1"))); });
        test("bounded newest-first search history",() -> { Fixture f=fixture(); for(int i=0;i<8;i++) f.p.searchText("C1","term"+i); check(f.c.searches().equals(List.of("term7","term6","term5","term4","term3")),"Last five"); });
        test("predicate search sorting cuisines and menu order",() -> { Fixture f=fixture(); f.p.addRestaurant(new Restaurant("R2","Alpha","Maadi",List.of("Egyptian","Italian"),4.8,true)); check(f.p.search(r -> r.district().equals("Maadi") && r.rating()>4.5).get(0).id().equals("R2"),"Tie by name"); check(f.p.cuisines().size()==3,"Distinct cuisines"); check(f.r.menu().stream().map(i -> i.key().itemId()).toList().equals(List.of("M1","M2","M3","M4")),"Insertion order"); });
        test("immutable exposed collections",() -> { Fixture f=fixture(); Order o=place(f); rejects(UnsupportedOperationException.class,() -> f.p.orders().clear()); rejects(UnsupportedOperationException.class,() -> f.r.menu().clear()); rejects(UnsupportedOperationException.class,() -> f.c.addresses().clear()); rejects(UnsupportedOperationException.class,() -> f.r.cuisines().clear()); rejects(UnsupportedOperationException.class,() -> o.lines().clear()); rejects(UnsupportedOperationException.class,() -> o.timeline().clear()); });
        test("entity equality hash codes and duplicate IDs",() -> { Fixture f=fixture(); Customer duplicate=new Customer("C1","Other","01112345678",List.of(ADDRESS),ONE); check(f.c.equals(duplicate) && f.c.hashCode()==duplicate.hashCode(),"Customer ID equality"); rejects(DuplicateIdException.class,() -> f.p.addCustomer(duplicate)); check(!f.r.item("M1").equals(new StandardItem(new ItemKey("R2","M1"),"Other","Main",1,ONE)),"Menu IDs scoped by restaurant"); check(new HashSet<>(List.of(f.c,duplicate)).size()==1,"Set deduplication"); });
        test("empty analytics express absence",() -> { Fixture f=fixture(); Reports r=new Reports(f.p); check(r.mostPopular().isEmpty() && r.peakHour().isEmpty(),"No invented values"); check(r.riderStats().get(0).averageDuration().isEmpty(),"No duration"); money("0",r.revenue(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31))); check(r.statusCounts().size()==8,"All statuses included"); });
        test("all ten reports on populated data",() -> { Fixture f=fixture(); for(int i=0;i<20;i++) complete(f,place(f)); Reports r=new Reports(f.p);
            BigDecimal revenue=f.p.orders().stream().map(o -> o.pricing().total()).reduce(Money.ZERO,BigDecimal::add);
            money(revenue.toString(),r.revenue(LocalDate.of(2026,1,15),LocalDate.of(2026,1,15)));
            check(r.topRestaurants(YearMonth.of(2026,1)).get(0).id().equals("R1"),"Top restaurant");
            money(revenue.divide(new BigDecimal("20"),2,java.math.RoundingMode.HALF_UP).toString(),r.averagePerDistrict().get("faisal"));
            check(r.highlyRated().size()==1,"High rating and 20 completions"); check(r.statusCounts().get(OrderStatus.DELIVERED)==20,"Status count");
            check(r.riderStats().get(0).completed()==20 && r.riderStats().get(0).averageDuration().orElseThrow().equals(Duration.ofMinutes(10)),"Rider stats");
            check(r.mostPopular().orElseThrow().orderCount()==20,"Popular item"); money(revenue.toString(),r.history("C1").totalSpent());
            check(r.history("C1").orders().get(0).id().equals("O000020"),"Newest history"); check(r.peakHour().orElseThrow()==12,"Peak hour tie chooses earliest");
            check(r.inactiveCustomers().isEmpty(),"Recent customer active"); f.clock.advance(Duration.ofDays(31)); check(r.inactiveCustomers().size()==1,"Inactive after 31 days");
        });
        test("revenue excludes cancelled and pending orders",() -> { Fixture f=fixture(); Order a=place(f); f.p.pay("C1",a.id()); Order b=place(f); f.p.pay("C1",b.id()); f.p.cancel("C1",b.id()); Reports r=new Reports(f.p); money("0",r.revenue(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31))); money("174",r.history("C1").totalSpent()); });
        test("inactive boundary and never-ordered customers",() -> { Fixture f=fixture(); Reports r=new Reports(f.p); check(r.inactiveCustomers().size()==1,"Never ordered"); place(f); f.clock.advance(Duration.ofDays(30)); check(r.inactiveCustomers().isEmpty(),"Exactly cutoff is active"); f.clock.advance(Duration.ofSeconds(1)); check(r.inactiveCustomers().size()==1,"Past cutoff inactive"); });
        test("factory and extension without pricing changes",() -> { Fixture f=fixture(); MenuItemFactory factory=new MenuItemFactory(); factory.register("special",s -> new MenuItem(s.key(),s.name(),s.category(),s.minutes()) { @Override public BigDecimal unitPrice() { return new BigDecimal("77.00"); } }); MenuItem item=factory.create("special",new MenuItemFactory.Spec(f.r,"S","Special","New",1,"","")); f.p.addItem("R1",item,ONE); money("77",f.p.place(builder(f).item("S",ONE).build()).pricing().subtotal()); });
        test("observer isolation and fourth reaction",() -> { Fixture f=fixture(); AtomicInteger reactions=new AtomicInteger(); f.p.events().subscribe(e -> { throw new IllegalStateException("Broken notifier"); }); f.p.events().subscribe(e -> reactions.incrementAndGet()); Order o=place(f); check(reactions.get()==1 && f.p.events().failures().size()==1,"Other observer ran"); check(f.p.order(o.id())==o,"Committed order retained"); });
        test("configuration singleton under concurrent access",() -> { Set<AppConfig> instances=ConcurrentHashMap.newKeySet(); race(() -> instances.add(AppConfig.get()),() -> instances.add(AppConfig.get())); check(instances.size()==1,"One config"); });
        test("historical price preserved after menu removal",() -> { Fixture f=fixture(); Order o=f.p.place(builder(f).item("M3",ONE).build()); f.p.removeItem("R1","M3"); money("400",o.pricing().subtotal()); f.p.cancel("C1",o.id()); money("100",f.p.stock(new ItemKey("R1","M3"))); });
        test("restaurant removal protects active orders and history",() -> { Fixture f=fixture(); Order o=place(f); rejects(ValidationException.class,() -> f.p.removeRestaurant("R1")); f.p.cancel("C1",o.id()); f.p.removeRestaurant("R1"); check(f.p.orders().size()==1,"History retained"); rejects(DuplicateIdException.class,() -> f.p.addRestaurant(f.r)); });
        System.out.println("\n" + passed + " tests passed.");
    }
}
