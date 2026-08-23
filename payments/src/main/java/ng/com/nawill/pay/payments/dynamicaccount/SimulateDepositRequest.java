package ng.com.nawill.pay.payments.dynamicaccount;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;

public record SimulateDepositRequest(@NotNull @Positive BigInteger amount) {
}
