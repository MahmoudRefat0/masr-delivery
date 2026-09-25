package masr.delivery;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record Promotion(String code, Policy policy, List<Condition> conditions) {
    public Promotion { code = Money.text(code, "Promotion code").toUpperCase(Locale.ROOT); Objects.requireNonNull(policy); conditions = List.copyOf(conditions); }
    public record Context(BigDecimal subtotal, BigDecimal deliveryFee, Address address, boolean firstOrder, LocalDate date) { }
    public record Benefit(BigDecimal subtotalDiscount, boolean freeDelivery) {
        public Benefit { if (subtotalDiscount == null || subtotalDiscount.signum() < 0) throw new ValidationException("Invalid promotion benefit."); }
    }
    @FunctionalInterface public interface Policy { Benefit apply(Context context); }
    @FunctionalInterface public interface Condition { void validate(Context context); }
    public Benefit apply(Context context) { conditions.forEach(c -> c.validate(context)); return policy.apply(context); }
    public static Policy percentage(BigDecimal rate, BigDecimal cap) {
        if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) throw new ValidationException("Percentage must be above 0 and at most 100.");
        Money.positive(cap, "Discount cap");
        return c -> new Benefit(Money.round(c.subtotal().multiply(rate).min(cap)), false);
    }
    public static Policy fixed(BigDecimal amount) { Money.positive(amount, "Discount"); return c -> new Benefit(Money.round(amount), false); }
    public static Policy freeDelivery() { return c -> new Benefit(Money.ZERO, true); }
    public static Condition minimum(BigDecimal amount) {
        Money.positive(amount, "Minimum subtotal");
        return c -> { if (c.subtotal().compareTo(amount) < 0) throw new InvalidPromotionException("Minimum subtotal is " + amount + " EGP."); };
    }
    public static Condition expires(LocalDate date) { Objects.requireNonNull(date); return c -> { if (c.date().isAfter(date)) throw new InvalidPromotionException("Promotion expired on " + date + "."); }; }
    public static Condition district(String district) { String valid = Money.text(district, "District"); return c -> { if (!c.address().district().equalsIgnoreCase(valid)) throw new InvalidPromotionException("Promotion is only valid in " + valid + "."); }; }
    public static Condition firstTime() { return c -> { if (!c.firstOrder()) throw new InvalidPromotionException("Promotion is for first-time customers only."); }; }
}
