package ng.com.nawill.pay.payments.thirdparty;

import jakarta.validation.constraints.Positive;
import java.math.BigInteger;

/** {@code amount} is optional - omitted means "settle the full available balance". */
public record WithdrawRequest(@Positive BigInteger amount) {
}
