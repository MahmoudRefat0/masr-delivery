package masr.delivery;

import java.math.BigDecimal;
public enum LoyaltyTier {
    BRONZE("1.00"), SILVER("0.90"), GOLD("0.00");
    private final BigDecimal deliveryMultiplier;
    LoyaltyTier(String multiplier) { deliveryMultiplier = new BigDecimal(multiplier); }
    public BigDecimal apply(BigDecimal fee) { return Money.round(fee.multiply(deliveryMultiplier)); }
    public static LoyaltyTier from(int completed) { return completed >= 30 ? GOLD : completed >= 10 ? SILVER : BRONZE; }
}
