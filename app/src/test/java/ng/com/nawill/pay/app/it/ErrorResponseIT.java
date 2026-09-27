package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Every failure - including those raised before a controller runs (security
 * filters, routing, content negotiation) - returns the catalogue's JSON body,
 * never an empty response or a 500 for a client mistake.
 */
class ErrorResponseIT extends AbstractIntegrationTest {

    @Test
    void rejectedOrMissingTokenGetsAJsonUnauthenticatedBody() {
        HttpHeaders garbage = new HttpHeaders();
        garbage.setBearerAuth("garbage");

        assertError(restTemplate.exchange(url("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(garbage), Map.class),
                ErrorCode.UNAUTHENTICATED);
        assertError(restTemplate.getForEntity(url("/api/v1/users/me"), Map.class), ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void routingAndContentMistakesAreClientErrorsNotServerErrors() {
        HttpHeaders auth = authHeaders(signupAndGetToken("Route", "Tester", "SecurePass123!"));
        HttpHeaders plainText = new HttpHeaders();
        plainText.setContentType(MediaType.TEXT_PLAIN);

        assertError(restTemplate.exchange(url("/api/v1/no-such-route"), HttpMethod.GET, new HttpEntity<>(auth), Map.class),
                ErrorCode.ROUTE_NOT_FOUND);
        assertError(restTemplate.exchange(url("/api/v1/users/me"), HttpMethod.DELETE, new HttpEntity<>(auth), Map.class),
                ErrorCode.METHOD_NOT_ALLOWED);
        assertError(restTemplate.exchange(url("/api/v1/auth/login"), HttpMethod.POST,
                new HttpEntity<>("not json", plainText), Map.class), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    private static void assertError(ResponseEntity<Map> response, ErrorCode expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected.status());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("errorCode")).isEqualTo(expected.name());
        assertThat(response.getBody().get("message")).isEqualTo(expected.message());
    }
}
