package ng.com.nawill.pay.onboarding.auth;

public final class AuthRedisConstants {

    public static final String LOGIN_ATTEMPTS_PREFIX = "auth:login-attempts:";
    public static final String LOGIN_LOCKOUT_PREFIX = "auth:login-lockout:";
    public static final String PASSWORD_RESET_PREFIX = "auth:password-reset:";
    public static final String TRANSACTION_PIN_ATTEMPTS_PREFIX = "auth:pin-attempts:";
    public static final String TRANSACTION_PIN_LOCKOUT_PREFIX = "auth:pin-lockout:";

    private AuthRedisConstants() {
    }
}
