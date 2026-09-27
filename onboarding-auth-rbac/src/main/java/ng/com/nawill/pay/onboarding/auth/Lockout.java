package ng.com.nawill.pay.onboarding.auth;

import java.time.Duration;

/** Shared by login and transaction-PIN lockouts for {@code ErrorCode.ACCOUNT_LOCKED}'s "try again in N minute(s)". */
final class Lockout {

    private Lockout() {
    }

    static long minutesRemaining(Duration remaining) {
        return Math.max(1, remaining.toMinutes());
    }
}
