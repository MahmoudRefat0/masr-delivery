package masr.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public final class Pricing {
    public record Breakdown(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal discount, BigDecimal total) { }
    private final AppConfig config;
    public Pricing(AppConfig config) { this.config = config; }
    public Breakdown calculate(List<Order.Line> lines, BigDecimal km, LoyaltyTier tier, Address address, boolean firstOrder, LocalDate date, Promotion promo) {
        BigDecimal subtotal = lines.stream().map(Order.Line::amount).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal delivery = tier.apply(config.baseFee().add(km.subtract(config.includedKm()).max(BigDecimal.ZERO).multiply(config.perKm())));
        BigDecimal service = Money.round(subtotal.multiply(config.serviceRate()));
        BigDecimal discount = Money.ZERO;
        if (promo != null) {
            Promotion.Benefit benefit = promo.apply(new Promotion.Context(subtotal, delivery, address, firstOrder, date));
            discount = Money.round(benefit.subtotalDiscount().min(subtotal));
            if (benefit.freeDelivery()) delivery = Money.ZERO;
        }
        return new Breakdown(subtotal, delivery, service, discount, Money.round(subtotal.add(delivery).add(service).subtract(discount).max(BigDecimal.ZERO)));
    }
}
