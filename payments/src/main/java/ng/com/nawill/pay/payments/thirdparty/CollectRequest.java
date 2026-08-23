package ng.com.nawill.pay.payments.thirdparty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;

public record CollectRequest(@NotNull @Positive BigInteger amount) {
}
