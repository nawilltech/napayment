package ng.com.nawill.pay.payments.bankverification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import ng.com.nawill.pay.common.config.PaystackProperties;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls Paystack's {@code GET /bank/resolve} (account Name Enquiry). Live
 * network call - never active under the {@code test} profile, see
 * {@link ng.com.nawill.pay.payments.bankverification.BankVerificationGateway}.
 */
@Component
@Profile("!test")
public class PaystackBankVerificationGateway implements BankVerificationGateway {

    private static final Logger log = LoggerFactory.getLogger(PaystackBankVerificationGateway.class);

    private final RestClient restClient;
    private final PaystackProperties paystack;

    public PaystackBankVerificationGateway(RestClient.Builder builder, PaystackProperties paystack) {
        this.restClient = builder.baseUrl(paystack.baseUrl()).build();
        this.paystack = paystack;
    }

    @Override
    public ResolvedAccount resolveAccountName(String accountNumber, String bankCode) {
        if (!paystack.configured()) {
            log.error("Paystack secret key not configured ({}) - bank verification is unavailable", PaystackProperties.SECRET_KEY_ENV);
            throw new ApiException(ErrorCode.BANK_VERIFICATION_UNAVAILABLE);
        }
        try {
            PaystackResolveResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/bank/resolve")
                            .queryParam("account_number", accountNumber)
                            .queryParam("bank_code", bankCode)
                            .build())
                    .header("Authorization", paystack.bearerToken())
                    .retrieve()
                    .body(PaystackResolveResponse.class);
            if (response == null || !response.status() || response.data() == null) {
                log.warn("paystack could not resolve account: message={}", response == null ? null : response.message());
                throw new ApiException(ErrorCode.BANK_VERIFICATION_FAILED);
            }
            return new ResolvedAccount(response.data().accountNumber(), response.data().accountName());
        } catch (RestClientResponseException e) {
            // Paystack's own wording (e.g. its test-mode daily limit) is logged, never shown to users.
            log.warn("paystack resolve failed: status={} message={}", e.getStatusCode().value(),
                    extractMessage(e).orElse(null));
            HttpStatusCode status = e.getStatusCode();
            boolean providerSide = status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
            throw new ApiException(providerSide ? ErrorCode.BANK_VERIFICATION_UNAVAILABLE : ErrorCode.BANK_VERIFICATION_FAILED);
        } catch (ResourceAccessException e) {
            log.warn("paystack unreachable: {}", e.getMessage());
            throw new ApiException(ErrorCode.BANK_VERIFICATION_UNAVAILABLE);
        }
    }

    private static Optional<String> extractMessage(RestClientResponseException e) {
        try {
            return Optional.ofNullable(e.getResponseBodyAs(PaystackResolveResponse.class))
                    .map(PaystackResolveResponse::message);
        } catch (Exception parseFailure) {
            return Optional.empty();
        }
    }

    private record PaystackResolveResponse(boolean status, String message, PaystackResolveData data) {
    }

    private record PaystackResolveData(
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("account_name") String accountName) {
    }
}
