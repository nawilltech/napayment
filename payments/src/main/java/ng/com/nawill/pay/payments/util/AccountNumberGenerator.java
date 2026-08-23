package ng.com.nawill.pay.payments.util;

import java.security.SecureRandom;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * CBN-NUBAN-style 10-digit account number generation ("9" + 9 random
 * digits), shared by every feature that mints one - {@code VirtualAccount}
 * (FR-1) and dynamic/temporary virtual accounts (FR-DynAcct-1) alike - so
 * the algorithm lives in exactly one place.
 */
@Component
public class AccountNumberGenerator {

    private final SecureRandom random = new SecureRandom();

    /** {@code alreadyExists} is a repository existence check for the caller's own table. */
    public String generateUnique(Predicate<String> alreadyExists) {
        String candidate;
        do {
            candidate = "9" + String.format("%09d", random.nextInt(1_000_000_000));
        } while (alreadyExists.test(candidate));
        return candidate;
    }
}
