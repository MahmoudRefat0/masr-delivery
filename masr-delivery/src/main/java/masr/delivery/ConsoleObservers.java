package masr.delivery;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ConsoleObservers {
    private final Map<String,String> riderDashboard = new LinkedHashMap<>();
    private Map<OrderStatus,Long> counts = Map.of();
    public ConsoleObservers(Platform platform) {
        platform.events().subscribe(e -> System.out.println("[Customer " + e.customerId() + "] " + e.orderId() + " -> " + e.after()));
        platform.events().subscribe(e -> {
            if (e.riderId() != null) riderDashboard.put(e.riderId(),e.orderId() + " " + e.after());
        });
        platform.events().subscribe(e -> {
            Path path = Path.of(AppConfig.get().auditPath());
            try {
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                Files.writeString(path,e.at() + " | " + e.orderId() + " | " + e.before() + " -> " + e.after() + System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            } catch (IOException failure) { throw new IllegalStateException("Audit write failed: " + failure.getMessage(),failure); }
        });
        platform.events().subscribe(e -> counts = new Reports(platform).statusCounts());
    }
    public Map<String,String> riderDashboard() { return Map.copyOf(riderDashboard); }
    public Map<OrderStatus,Long> statusCounts() { return counts; }
}
