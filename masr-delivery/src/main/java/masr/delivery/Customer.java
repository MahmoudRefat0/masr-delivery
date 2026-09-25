package masr.delivery;

import java.math.BigDecimal;
import java.util.*;

public final class Customer extends Entity {
    private final String name;
    private final String mobile;
    private final LinkedHashSet<Address> addresses;
    private final Deque<String> searches = new ArrayDeque<>();
    private BigDecimal wallet;
    private int completedOrders;
    public Customer(String id, String name, String mobile, Collection<Address> addresses, BigDecimal wallet) {
        super(id);
        this.name = Money.text(name, "Name");
        if (mobile == null || !mobile.matches("01[0125][0-9]{8}")) throw new ValidationException("Use an 11-digit Egyptian mobile number.");
        this.mobile = mobile;
        if (addresses == null || addresses.isEmpty() || addresses.stream().anyMatch(Objects::isNull)) throw new ValidationException("At least one valid address is required.");
        this.addresses = new LinkedHashSet<>(addresses);
        if (wallet == null || wallet.signum() < 0) throw new ValidationException("Wallet cannot be negative.");
        this.wallet = Money.round(wallet);
    }
    public String name() { return name; }
    public String mobile() { return mobile; }
    public Set<Address> addresses() { return Collections.unmodifiableSet(new LinkedHashSet<>(addresses)); }
    public synchronized BigDecimal wallet() { return wallet; }
    public synchronized int completedOrders() { return completedOrders; }
    public synchronized LoyaltyTier tier() { return LoyaltyTier.from(completedOrders); }
    public synchronized List<String> searches() { return List.copyOf(searches); }
    synchronized void search(String text) { searches.addFirst(text); if (searches.size() > 5) searches.removeLast(); }
    synchronized void debit(BigDecimal amount) {
        if (wallet.compareTo(amount) < 0) throw new InsufficientFundsException("Wallet balance is " + wallet + " EGP; required " + amount + ".");
        wallet = wallet.subtract(amount);
    }
    synchronized void credit(BigDecimal amount) { wallet = wallet.add(amount); }
    synchronized void completed() { completedOrders++; }
}
