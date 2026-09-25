package masr.delivery;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private Money() { }
    public static BigDecimal of(String text) { return round(new BigDecimal(text)); }
    public static BigDecimal round(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal positive(BigDecimal amount, String field) {
        if (amount == null || amount.signum() <= 0) throw new ValidationException(field + " must be positive.");
        return amount;
    }
    public static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new ValidationException(field + " is required.");
        return value.trim();
    }
}
