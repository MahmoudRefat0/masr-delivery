package masr.delivery;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
public final class OrderEvents {
    public record Event(String orderId, String customerId, String riderId, OrderStatus before, OrderStatus after, Instant at) { }
    private final List<Consumer<Event>> listeners = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    public synchronized void subscribe(Consumer<Event> listener) { listeners.add(Objects.requireNonNull(listener)); }
    synchronized void publish(Event event) {
        for (Consumer<Event> listener : List.copyOf(listeners)) {
            try { listener.accept(event); }
            catch (RuntimeException ex) { failures.add(event.orderId() + ": " + ex.getMessage()); }
        }
    }
    public synchronized List<String> failures() { return List.copyOf(failures); }
}
