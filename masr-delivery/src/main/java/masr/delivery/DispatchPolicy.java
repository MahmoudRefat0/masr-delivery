package masr.delivery;

import java.math.BigDecimal;
public interface DispatchPolicy {
    String name();
    boolean eligible(BigDecimal kilometres, BigDecimal load);
    int estimatedMinutes(BigDecimal kilometres);
}
