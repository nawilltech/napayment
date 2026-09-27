package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Guards the API docs contract: every operation has a brief summary and a
 * stated auth type derived from the enforcing code (OpenApiConfig).
 */
class OpenApiDocsIT extends AbstractIntegrationTest {

    @Test
    void everyOperationHasASummaryAndAnAuthType() {
        Map<String, Map<String, Map<String, Object>>> paths = paths();
        List<String> undocumented = new ArrayList<>();

        paths.forEach((path, operations) -> operations.forEach((method, operation) -> {
            Object summary = operation.get("summary");
            if (summary == null || summary.toString().isBlank() || operation.get("x-auth") == null
                    || !operation.get("description").toString().startsWith("**Auth:**")) {
                undocumented.add(method.toUpperCase() + " " + path);
            }
        }));

        assertThat(paths).isNotEmpty();
        assertThat(undocumented).as("operations missing a summary or auth").isEmpty();
    }

    @Test
    void authTypeAndSecurityMatchWhatIsEnforced() {
        Map<String, Map<String, Map<String, Object>>> paths = paths();

        assertAuth(paths.get("/api/v1/auth/login").get("post"), "PUBLIC");
        assertThat((List<?>) paths.get("/api/v1/auth/login").get("post").get("security")).isEmpty();
        assertAuth(paths.get("/api/v1/pay/{shortCode}").get("get"), "PUBLIC");

        Map<String, Object> collect = paths.get("/api/v1/collect").get("post");
        assertAuth(collect, "API_KEY");
        assertThat(collect.get("security").toString()).contains("apiKeySignature").doesNotContain("bearerAuth");

        Map<String, Object> approveKyc = paths.get("/api/v1/admin/businesses/{id}/kyc/approve").get("post");
        assertAuth(approveKyc, "ADMIN");
        assertThat(approveKyc.get("x-required-permission")).isEqualTo("platform-kyc:review");
        assertAuth(paths.get("/api/v1/collection-account").get("post"), "ADMIN");
        assertAuth(paths.get("/api/v1/payment-processors").get("post"), "ADMIN");

        Map<String, Object> bankAccounts = paths.get("/api/v1/bank-accounts").get("post");
        assertAuth(bankAccounts, "USER");
        assertThat(bankAccounts.get("x-required-permission")).isEqualTo("settlements:manage");
        assertThat(bankAccounts.get("security").toString()).contains("bearerAuth");
        assertAuth(paths.get("/api/v1/users/me").get("get"), "USER");
    }

    private static void assertAuth(Map<String, Object> operation, String expected) {
        assertThat(operation.get("x-auth")).isEqualTo(expected);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Map<String, Object>>> paths() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/v3/api-docs"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Map<String, Map<String, Object>>>) response.getBody().get("paths");
    }
}
