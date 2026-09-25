package masr.delivery;

import java.math.BigDecimal;
import java.math.RoundingMode;
public record VehiclePolicy(String name, BigDecimal maxKm, BigDecimal speedKph, BigDecimal maxLoad) implements DispatchPolicy {
    public VehiclePolicy { name = Money.text(name, "Vehicle name"); Money.positive(maxKm,"Range"); Money.positive(speedKph,"Speed"); Money.positive(maxLoad,"Capacity"); }
    public static VehiclePolicy motorcycle() { return new VehiclePolicy("motorcycle", new BigDecimal("25"), new BigDecimal("30"), new BigDecimal("30")); }
    public static VehiclePolicy bicycle() { return new VehiclePolicy("bicycle", new BigDecimal("5"), new BigDecimal("12"), new BigDecimal("8")); }
    public static VehiclePolicy car() { return new VehiclePolicy("car", new BigDecimal("60"), new BigDecimal("25"), new BigDecimal("100")); }
    @Override public boolean eligible(BigDecimal km, BigDecimal load) { return km.compareTo(maxKm) <= 0 && load.compareTo(maxLoad) <= 0; }
    @Override public int estimatedMinutes(BigDecimal km) { return km.multiply(new BigDecimal("60")).divide(speedKph, 0, RoundingMode.CEILING).intValueExact(); }
}
