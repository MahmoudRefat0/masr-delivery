package masr.delivery;

import java.io.*;
import java.math.BigDecimal;
import java.util.Properties;

public final class AppConfig {
    private final Properties properties = new Properties();
    private AppConfig() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) throw new IllegalStateException("Missing application.properties");
            properties.load(in);
        } catch (IOException e) { throw new IllegalStateException("Cannot load configuration", e); }
        Money.positive(baseFee(), "Base fee"); Money.positive(perKm(), "Per-km fee");
        Money.positive(includedKm(), "Included km"); Money.positive(serviceRate(), "Service rate");
        if (poolSize() <= 0) throw new IllegalStateException("pool.size must be positive");
    }
    private static class Holder { private static final AppConfig INSTANCE = new AppConfig(); }
    public static AppConfig get() { return Holder.INSTANCE; }
    public BigDecimal baseFee() { return new BigDecimal(properties.getProperty("delivery.base")); }
    public BigDecimal perKm() { return new BigDecimal(properties.getProperty("delivery.perKm")); }
    public BigDecimal includedKm() { return new BigDecimal(properties.getProperty("delivery.includedKm")); }
    public BigDecimal serviceRate() { return new BigDecimal(properties.getProperty("service.rate")); }
    public int poolSize() { return Integer.parseInt(properties.getProperty("pool.size")); }
    public String menuResource() { return properties.getProperty("menu.resource"); }
    public String auditPath() { return properties.getProperty("audit.path"); }
}
