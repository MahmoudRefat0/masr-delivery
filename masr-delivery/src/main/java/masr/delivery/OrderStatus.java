package masr.delivery;

public enum OrderStatus {
    PLACED, ACCEPTED, PREPARING, READY, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;
    public boolean terminal() { return this == DELIVERED || this == CANCELLED; }
    public boolean canMoveTo(OrderStatus next) {
        if (terminal()) return false;
        if (next == CANCELLED) return ordinal() < OUT_FOR_DELIVERY.ordinal();
        return next.ordinal() == ordinal() + 1 && next != CANCELLED;
    }
}
