package ng.com.nawill.pay.payments.settlement;

import java.math.BigInteger;
import ng.com.nawill.pay.payments.bankaccount.BankAccount;

/**
 * Behind-an-interface contract for actually moving money out to a real bank
 * account (NIBSS NIP disbursement), mirroring
 * {@code PaymentProcessorGateway}'s dependency-inverted shape.
 * {@link SandboxSettlementGateway} is the only implementation for MVP
 * v0.1; a real NIBSS integration is v0.2+ (FR-Settle-2).
 */
public interface SettlementGateway {

    DisbursementResult disburse(BigInteger amount, BankAccount destination);

    record DisbursementResult(String reference, boolean successful) {
    }
}
