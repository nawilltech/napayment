package ng.com.nawill.pay.payments.transfer;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a transfer recipient's identity across the module boundary.
 * {@code VirtualAccount.userId} is a bare UUID column by design (see its own
 * javadoc) - payments never reaches into onboarding-auth-rbac's {@code User}
 * entity directly, even though onboarding-auth-rbac is the module that
 * happens to depend on payments, not the other way round. Implemented there
 * anyway (doc 2 §7 ADR-12): the interface lives with its consumer, same as
 * {@link ng.com.nawill.pay.payments.processor.PaymentProcessorGateway}, and
 * Spring's whole-application component scan (see {@code NawillPayApplication})
 * wires the two together at runtime regardless of Maven's compile-time
 * dependency direction.
 */
public interface RecipientDirectory {

    Optional<UUID> resolveUserIdByPhone(String phoneNo);

    Optional<RecipientName> nameFor(UUID userId);

    record RecipientName(String firstName, String lastName) {
    }
}
