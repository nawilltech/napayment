package ng.com.nawill.pay.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Paystack settings, bound once at startup from {@code nawill.paystack.*}
 * (application.yml maps them to environment variables). Twelve-factor: the
 * app is handed one secret key and never chooses between test and live -
 * each environment supplies its own (local .env, the GitHub Environment's
 * API_ENV_FILE on servers).
 *
 * @param secretKey blank = Paystack not configured (name enquiry unavailable, BVN checks fall back)
 */
@Validated
@ConfigurationProperties(prefix = "nawill.paystack")
public record PaystackProperties(@NotBlank String baseUrl, String secretKey) {

    /** The environment variable application.yml binds the secret key from, named in log messages. */
    public static final String SECRET_KEY_ENV = "PAYSTACK_PRIVATE_KEY";

    public boolean configured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /** Value for the Authorization header on Paystack API calls. */
    public String bearerToken() {
        return "Bearer " + secretKey;
    }

    @Override
    public String toString() {
        // Never let the secret reach a log line via toString().
        return "PaystackProperties[baseUrl=" + baseUrl + ", configured=" + configured() + "]";
    }
}
