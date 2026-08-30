package ng.com.nawill.pay.payments.bankverification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import ng.com.nawill.pay.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
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

    private final RestClient restClient;
    private final String secretKey;

    public PaystackBankVerificationGateway(RestClient.Builder builder,
                                            @Value("${nawill.paystack.base-url}") String baseUrl,
                                            @Value("${nawill.paystack.secret-key:}") String secretKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.secretKey = secretKey;
    }

    @Override
    public ResolvedAccount resolveAccountName(String accountNumber, String bankCode) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Paystack secret key not configured (PAYSTACK_TEST_PRIVATE_KEY) - bank verification is unavailable");
        }
        try {
            PaystackResolveResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/bank/resolve")
                            .queryParam("account_number", accountNumber)
                            .queryParam("bank_code", bankCode)
                            .build())
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(PaystackResolveResponse.class);
            if (response == null || !response.status() || response.data() == null) {
                String message = response == null ? "Empty response from Paystack" : response.message();
                throw new BadRequestException("BANK_VERIFICATION_FAILED", message);
            }
            return new ResolvedAccount(response.data().accountNumber(), response.data().accountName());
        } catch (RestClientResponseException e) {
            String message = extractMessage(e).orElse("Could not resolve account name");
            throw new BadRequestException("BANK_VERIFICATION_FAILED", message);
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
