package ng.com.nawill.pay.payments.settlement;

import java.math.BigInteger;
import java.util.UUID;
import ng.com.nawill.pay.payments.bankaccount.BankAccount;
import org.springframework.stereotype.Component;

/**
 * Fake/sandbox settlement gateway for MVP v0.1 - always synchronously
 * "succeeds" with a generated reference. No real NIBSS NIP call is made.
 * TODO(FR-Settle-2): replace with a real NIBSS disbursement integration.
 */
@Component
public class SandboxSettlementGateway implements SettlementGateway {

    @Override
    public DisbursementResult disburse(BigInteger amount, BankAccount destination) {
        return new DisbursementResult("sandbox_settlement_" + UUID.randomUUID(), true);
    }
}
