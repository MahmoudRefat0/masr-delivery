package masr.delivery;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;

public final class Main {
    private final Scanner input = new Scanner(System.in);
    private final Platform platform;
    private final Reports reports;
    private final ConsoleObservers observers;
    private final MenuItemFactory factory = new MenuItemFactory();
    private static final class EndOfInput extends RuntimeException { }
    private Main(Platform platform) { this.platform = platform; reports = new Reports(platform); observers = new ConsoleObservers(platform); }
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--demo")) { workedExample(); return; }
        Main app = new Main(DemoData.create());
        try { app.run(); } catch (EndOfInput end) { System.out.println("\nInput closed. Goodbye."); }
    }
    private static void workedExample() {
        Platform p = DemoData.create();
        Order order = p.place(new Order.Builder().customer(p.customer("C1")).restaurant(p.restaurant("R1"))
                .address(p.customer("C1").addresses().iterator().next()).item("M1",new BigDecimal("2")).promotion("NILE20").build());
        summary(order);
        if (order.pricing().total().compareTo(new BigDecimal("258.00")) != 0) throw new AssertionError("Worked example failed");
        System.out.println("Worked example verified: 258.00 EGP");
    }
    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        if (!input.hasNextLine()) throw new EndOfInput();
        return input.nextLine().trim();
    }
    private String required(String prompt) { return Money.text(ask(prompt),prompt); }
    private int option(String prompt,int min,int max) {
        while (true) {
            try { int value = Integer.parseInt(ask(prompt)); if (value >= min && value <= max) return value; }
            catch (NumberFormatException ignored) { }
            System.out.println("Choose a number from " + min + " to " + max + ".");
        }
    }
    private BigDecimal decimal(String prompt) { return new BigDecimal(required(prompt)); }
    private void attempt(Runnable action) {
        try { action.run(); }
        catch (PlatformException | IllegalArgumentException | DateTimeParseException ex) { System.out.println("Error: " + ex.getMessage()); }
    }
    private void run() {
        System.out.println("MASR DELIVERY | amounts in EGP | demo data loaded");
        System.out.println("Customers: C1 Omar, C2 Salma. Restaurants: R1, R2, R3. Riders: D1, D2, D3.");
        System.out.println("Riders start off duty. Pay an order before restaurant acceptance.");
        while (true) {
            System.out.println("\n1. Customer\n2. Restaurant\n3. Rider\n4. Admin & Reports\n0. Exit");
            int choice = option("Choose",0,4); if (choice == 0) { System.out.println("Goodbye."); return; }
            attempt(() -> { switch (choice) { case 1 -> customerArea(); case 2 -> restaurantArea(); case 3 -> riderArea(); case 4 -> adminArea(); default -> { } } });
        }
    }
    private void customerArea() {
        platform.customers().forEach(c -> System.out.println(c.id() + " | " + c.name() + " | " + c.tier() + " | wallet " + c.wallet()));
        Customer customer = platform.customer(required("Customer ID"));
        while (true) {
            System.out.println("\nCUSTOMER " + customer.name() + " | " + customer.tier() + " | wallet " + customer.wallet());
            System.out.println("1. Browse restaurants\n2. Search\n3. View menu\n4. Place order\n5. Pay from wallet\n6. Track order\n7. Cancel order\n8. Order history\n9. Last five searches\n0. Back");
            int choice = option("Choose",0,9); if (choice == 0) return;
            attempt(() -> {
                switch (choice) {
                    case 1 -> browse();
                    case 2 -> showRestaurants(platform.searchText(customer.id(),required("Search name or cuisine")));
                    case 3 -> showMenu(selectRestaurant());
                    case 4 -> place(customer);
                    case 5 -> { platform.pay(customer.id(),required("Order reference")); System.out.println("Payment confirmed. Wallet: " + customer.wallet() + " EGP"); }
                    case 6 -> {
                        Order order = platform.track(customer.id(),required("Order reference")); summary(order);
                        System.out.println("Elapsed: " + Duration.between(order.placedAt(),platform.clock().instant()).toMinutes() + " minute(s)");
                        order.timeline().forEach((status,time) -> System.out.println(status + " at " + time.atZone(platform.clock().getZone())));
                    }
                    case 7 -> { platform.cancel(customer.id(),required("Order reference")); System.out.println("Cancelled. Any paid amount was refunded. Wallet: " + customer.wallet()); }
                    case 8 -> showHistory(customer.id());
                    case 9 -> showList(customer.searches());
                    default -> { }
                }
            });
        }
    }
    private void browse() {
        String district = ask("District (blank = any)"); String cuisine = ask("Cuisine (blank = any)");
        String ratingText = ask("Minimum rating (blank = any)"); String priceText = ask("Maximum item price (blank = any)");
        Predicate<Restaurant> condition = Restaurant::open;
        if (!district.isBlank()) condition = condition.and(r -> r.district().equalsIgnoreCase(district));
        if (!cuisine.isBlank()) condition = condition.and(r -> r.cuisines().stream().anyMatch(c -> c.equalsIgnoreCase(cuisine)));
        if (!ratingText.isBlank()) {
            double rating = Double.parseDouble(ratingText);
            if (!Double.isFinite(rating) || rating < 0 || rating > 5) throw new ValidationException("Rating must be 0 to 5.");
            condition = condition.and(r -> r.rating() >= rating);
        }
        if (!priceText.isBlank()) {
            BigDecimal ceiling = Money.positive(new BigDecimal(priceText),"Price ceiling");
            condition = condition.and(r -> r.menu().stream().anyMatch(i -> i.available() && i.unitPrice().compareTo(ceiling) <= 0));
        }
        showRestaurants(platform.search(condition));
    }
    private void showRestaurants(List<Restaurant> restaurants) {
        if (restaurants.isEmpty()) { System.out.println("Nothing found."); return; }
        for (int i=0;i<restaurants.size();i++) System.out.println((i+1) + ". " + restaurants.get(i));
    }
    private Restaurant selectRestaurant() {
        List<Restaurant> restaurants = platform.search(r -> true); showRestaurants(restaurants);
        if (restaurants.isEmpty()) throw new NotFoundException("No restaurants available.");
        return restaurants.get(option("Restaurant number",1,restaurants.size())-1);
    }
    private static void showMenu(Restaurant restaurant) {
        System.out.println("MENU | " + restaurant.name() + (restaurant.open() ? "" : " [CLOSED]"));
        String category = null;
        for (MenuItem item : restaurant.menu()) {
            if (!item.category().equals(category)) { category = item.category(); System.out.println("-- " + category + " --"); }
            System.out.println(item + " | preparation " + item.preparationMinutes() + " min");
        }
        if (restaurant.menu().isEmpty()) System.out.println("Menu is empty.");
    }
    private void place(Customer customer) {
        Restaurant restaurant = selectRestaurant(); showMenu(restaurant);
        List<Address> addresses = List.copyOf(customer.addresses());
        for (int i=0;i<addresses.size();i++) System.out.println((i+1) + ". " + addresses.get(i));
        Order.Builder builder = new Order.Builder().customer(customer).restaurant(restaurant).address(addresses.get(option("Address number",1,addresses.size())-1));
        while (true) {
            String item = ask("Item ID (blank = finish)"); if (item.isBlank()) break;
            restaurant.item(item); builder.item(item,decimal("Quantity (kg for weighted items)"));
        }
        builder.promotion(ask("Promotion code (optional)")).notes(ask("Delivery notes (optional)"));
        summary(platform.place(builder.build()));
        System.out.println("Stock reserved. Use Pay from wallet before acceptance.");
    }
    private static void summary(Order order) {
        System.out.println("\n" + order);
        order.lines().forEach(line -> System.out.println(line.name() + " x " + line.quantity() + " = " + line.amount() + " EGP"));
        Pricing.Breakdown price = order.pricing();
        System.out.println("Subtotal: " + price.subtotal() + "\nDelivery: " + price.deliveryFee() + "\nService: " + price.serviceFee() + "\nPromotion discount: " + price.discount() + "\nTOTAL: " + price.total() + " EGP");
        System.out.println("Address: " + order.address() + " | distance " + order.kilometres() + " km");
        if (!order.notes().isBlank()) System.out.println("Notes: " + order.notes());
    }
    private void showHistory(String id) {
        Reports.CustomerHistory history = reports.history(id); showList(history.orders()); System.out.println("Total spent (paid, excluding refunds): " + history.totalSpent() + " EGP");
    }
    private void restaurantArea() {
        Restaurant restaurant = selectRestaurant();
        while (true) {
            System.out.println("\nRESTAURANT " + restaurant.name());
            System.out.println("1. Accept order\n2. Reject pending order\n3. Mark preparing\n4. Mark ready\n5. Toggle item availability\n6. Add menu item\n7. Remove menu item\n8. Adjust available stock\n9. Today's orders and revenue\n10. Toggle open/closed\n11. View menu and stock\n0. Back");
            int choice = option("Choose",0,11); if (choice == 0) return;
            attempt(() -> {
                switch (choice) {
                    case 1,2,3,4 -> {
                        OrderStatus next = switch (choice) { case 1 -> OrderStatus.ACCEPTED; case 2 -> OrderStatus.CANCELLED; case 3 -> OrderStatus.PREPARING; default -> OrderStatus.READY; };
                        platform.restaurantStatus(restaurant.id(),required("Order reference"),next); System.out.println("Updated.");
                    }
                    case 5 -> { MenuItem item = restaurant.item(required("Item ID")); platform.setAvailable(restaurant.id(),item.key().itemId(),!item.available()); System.out.println(item); }
                    case 6 -> addItem(restaurant);
                    case 7 -> { platform.removeItem(restaurant.id(),required("Item ID")); System.out.println("Removed."); }
                    case 8 -> { platform.setStock(restaurant.id(),required("Item ID"),decimal("New remaining stock (excludes existing reservations)")); System.out.println("Stock updated."); }
                    case 9 -> {
                        LocalDate today = LocalDate.now(platform.clock());
                        List<Order> orders = platform.orders().stream().filter(o -> o.restaurant().equals(restaurant)).toList();
                        showList(orders.stream().filter(o -> o.placedAt().atZone(platform.clock().getZone()).toLocalDate().equals(today)).toList());
                        BigDecimal revenue = orders.stream().filter(o -> o.status() == OrderStatus.DELIVERED).filter(o -> o.time(OrderStatus.DELIVERED).orElseThrow().atZone(platform.clock().getZone()).toLocalDate().equals(today)).map(o -> o.pricing().total()).reduce(Money.ZERO,BigDecimal::add);
                        System.out.println("Revenue from today's deliveries: " + revenue + " EGP");
                    }
                    case 10 -> { platform.setOpen(restaurant.id(),!restaurant.open()); System.out.println(restaurant.open() ? "Open" : "Closed"); }
                    case 11 -> { showMenu(restaurant); restaurant.menu().forEach(i -> System.out.println(i.key() + " stock: " + (i instanceof ComboItem ? "from components" : platform.stock(i.key())))); }
                    default -> { }
                }
            });
        }
    }
    private void addItem(Restaurant restaurant) {
        String type = required("Type: standard / weighted / combo"); String id = required("Item ID");
        String name = required("Name"); String category = required("Category"); int minutes = option("Preparation minutes",0,1440);
        String value = required(type.equalsIgnoreCase("combo") ? "Discount fraction, e.g. 0.10" : "Price in EGP (per kg for weighted)");
        String parts = type.equalsIgnoreCase("combo") ? required("Components, e.g. M1:1,M2:1") : "";
        MenuItem item = factory.create(type,new MenuItemFactory.Spec(restaurant,id,name,category,minutes,value,parts));
        BigDecimal stock = item instanceof ComboItem ? BigDecimal.ZERO : decimal("Initial stock");
        platform.addItem(restaurant.id(),item,stock); System.out.println("Added: " + item);
    }
    private void riderArea() {
        showList(platform.riders()); Rider rider = platform.rider(required("Rider ID"));
        while (true) {
            System.out.println("\nRIDER " + rider.name() + " | district " + rider.district());
            System.out.println("1. Go on duty\n2. Go off duty\n3. View active order\n4. Mark picked up\n5. Mark delivered\n6. Personal statistics\n7. Update district\n0. Back");
            int choice = option("Choose",0,7); if (choice == 0) return;
            attempt(() -> {
                switch (choice) {
                    case 1 -> { platform.duty(rider.id(),true); System.out.println("On duty. Dispatch checked."); }
                    case 2 -> { platform.duty(rider.id(),false); System.out.println("Off duty."); }
                    case 3 -> { if (rider.activeOrder().isPresent()) summary(platform.order(rider.activeOrder().orElseThrow())); else System.out.println("No active order."); }
                    case 4 -> { platform.pickup(rider.id()); System.out.println("Picked up."); }
                    case 5 -> { platform.deliver(rider.id()); System.out.println("Delivered."); }
                    case 6 -> reports.riderStats().stream().filter(s -> s.id().equals(rider.id())).forEach(System.out::println);
                    case 7 -> { platform.relocate(rider.id(),required("Current district")); System.out.println("District updated."); }
                    default -> { }
                }
            });
        }
    }
    private void adminArea() {
        while (true) {
            System.out.println("\nADMIN\n1. Add restaurant\n2. Remove restaurant\n3. Create promotion\n4. Reports\n5. Platform statistics\n6. Register customer\n7. Register rider\n0. Back");
            int choice = option("Choose",0,7); if (choice == 0) return;
            attempt(() -> {
                switch (choice) {
                    case 1 -> {
                        String id = required("Restaurant ID"), name = required("Name"), district = required("District"), cuisines = required("Cuisines, comma separated");
                        double rating = Double.parseDouble(required("Rating 0 to 5"));
                        platform.addRestaurant(new Restaurant(id,name,district,Arrays.asList(cuisines.split(",")),rating,true)); System.out.println("Restaurant added.");
                    }
                    case 2 -> { platform.removeRestaurant(required("Restaurant ID")); System.out.println("Removed. Historical orders retained."); }
                    case 3 -> createPromotion();
                    case 4 -> reportArea();
                    case 5 -> {
                        System.out.println("Restaurants: " + platform.restaurants().size() + " | Customers: " + platform.customers().size() + " | Riders: " + platform.riders().size());
                        System.out.println("Cuisines: " + platform.cuisines() + "\nOrders: " + reports.statusCounts());
                        System.out.println("Dispatch queue:"); showList(platform.dispatchQueue());
                        System.out.println("Rider dashboard: " + observers.riderDashboard());
                        System.out.println("Event statistics: " + observers.statusCounts());
                        System.out.println("Observer failures: " + platform.events().failures());
                    }
                    case 6 -> {
                        String id = required("Customer ID"), name = required("Name"), mobile = required("Mobile");
                        Address address = new Address(required("District"),required("Address details"));
                        platform.addCustomer(new Customer(id,name,mobile,List.of(address),decimal("Opening wallet balance"))); System.out.println("Customer added.");
                    }
                    case 7 -> {
                        String id = required("Rider ID"), name = required("Name");
                        int vehicle = option("Vehicle: 1 motorcycle, 2 bicycle, 3 car",1,3);
                        DispatchPolicy policy = switch (vehicle) { case 1 -> VehiclePolicy.motorcycle(); case 2 -> VehiclePolicy.bicycle(); default -> VehiclePolicy.car(); };
                        platform.addRider(new Rider(id,name,policy,required("District"))); System.out.println("Rider added (off duty).");
                    }
                    default -> { }
                }
            });
        }
    }
    private void createPromotion() {
        String code = required("Code"); int type = option("1 percentage, 2 fixed, 3 free delivery",1,3);
        Promotion.Policy policy = switch (type) {
            case 1 -> Promotion.percentage(decimal("Percentage, e.g. 20").movePointLeft(2),decimal("Maximum discount in EGP"));
            case 2 -> Promotion.fixed(decimal("Discount in EGP"));
            default -> Promotion.freeDelivery();
        };
        List<Promotion.Condition> conditions = new ArrayList<>();
        String minimum = ask("Minimum subtotal (blank = none)"); if (!minimum.isBlank()) conditions.add(Promotion.minimum(new BigDecimal(minimum)));
        String expiry = ask("Expiry yyyy-MM-dd (blank = none)"); if (!expiry.isBlank()) conditions.add(Promotion.expires(LocalDate.parse(expiry)));
        String district = ask("Delivery district restriction (blank = none)"); if (!district.isBlank()) conditions.add(Promotion.district(district));
        if (option("First-time customers only? 1 yes, 0 no",0,1) == 1) conditions.add(Promotion.firstTime());
        platform.addPromotion(new Promotion(code,policy,conditions)); System.out.println("Promotion created.");
    }
    private void reportArea() {
        while (true) {
            System.out.println("\nREPORTS\n1. Revenue by date range\n2. Top five restaurants by month\n3. Average order value per district\n4. Highly rated restaurants with 20+ deliveries\n5. Counts by status\n6. Rider statistics\n7. Most frequently ordered item\n8. Customer history\n9. Peak ordering hour\n10. Inactive customers\n0. Back");
            int choice = option("Choose",0,10); if (choice == 0) return;
            attempt(() -> {
                switch (choice) {
                    case 1 -> System.out.println(reports.revenue(LocalDate.parse(required("From yyyy-MM-dd")),LocalDate.parse(required("To yyyy-MM-dd"))) + " EGP");
                    case 2 -> showList(reports.topRestaurants(YearMonth.parse(required("Month yyyy-MM"))));
                    case 3 -> System.out.println(reports.averagePerDistrict());
                    case 4 -> showList(reports.highlyRated());
                    case 5 -> System.out.println(reports.statusCounts());
                    case 6 -> showList(reports.riderStats());
                    case 7 -> System.out.println(reports.mostPopular().map(Object::toString).orElse("No ordered item exists."));
                    case 8 -> showHistory(required("Customer ID"));
                    case 9 -> { OptionalInt peak = reports.peakHour(); System.out.println(peak.isPresent() ? String.format("%02d:00",peak.getAsInt()) : "No orders exist."); }
                    case 10 -> reports.inactiveCustomers().forEach(c -> System.out.println(c.id() + " | " + c.name()));
                    default -> { }
                }
            });
        }
    }
    private static void showList(List<?> values) { if (values.isEmpty()) System.out.println("No records found."); else values.forEach(System.out::println); }
}
