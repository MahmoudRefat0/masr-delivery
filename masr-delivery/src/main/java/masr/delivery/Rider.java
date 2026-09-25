package masr.delivery;

import java.util.Optional;
public final class Rider extends Entity {
    private final String name;
    private final DispatchPolicy policy;
    private String district;
    private boolean onDuty;
    private String activeOrder;
    private int completed;
    public Rider(String id, String name, DispatchPolicy policy, String district) {
        super(id); this.name = Money.text(name, "Rider name"); this.policy = java.util.Objects.requireNonNull(policy); this.district = Money.text(district, "District");
    }
    public String name() { return name; }
    public DispatchPolicy policy() { return policy; }
    public synchronized String district() { return district; }
    public synchronized boolean available() { return onDuty && activeOrder == null; }
    public synchronized boolean onDuty() { return onDuty; }
    public synchronized int completedDeliveries() { return completed; }
    public synchronized Optional<String> activeOrder() { return Optional.ofNullable(activeOrder); }
    synchronized void duty(boolean value) {
        if (!value && activeOrder != null) throw new RiderBusyException("Finish the active order before going off duty.");
        onDuty = value;
    }
    synchronized void assign(String orderId) {
        if (!available()) throw new RiderBusyException("Rider " + id() + " is busy or off duty."); activeOrder = orderId;
    }
    synchronized void relocate(String value) { district = Money.text(value, "District"); }
    synchronized void release(boolean delivered, String destination) {
        activeOrder = null; if (delivered) { completed++; district = destination; }
    }
    @Override public String toString() { return id() + " | " + name + " | " + policy.name() + " | " + district() + " | " + (available() ? "available" : "busy/off duty"); }
}
