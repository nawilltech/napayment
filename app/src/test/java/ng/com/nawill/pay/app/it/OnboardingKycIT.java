package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-8: business KYC details, owner identity (BVN/NIN, mocked verification),
 * document upload/download, and submit-for-review - the backend surface
 * that replaces napayment-fe's dev-store stand-in for these onboarding steps.
 */
class OnboardingKycIT extends AbstractIntegrationTest {

    private String token;
    private UUID countryId;
    private UUID stateId;

    @BeforeEach
    void setUp() {
        token = businessSignupAndGetToken("Ada", "Lovelace", "SecurePass123!");
        countryId = insertCountry();
        stateId = insertState(countryId);
    }

    @Test
    void businessKycDetailsRoundTrip() {
        ResponseEntity<Map> before = restTemplate.exchange(
                url("/api/v1/business/kyc/details"), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(before.getBody()).isNull();

        Map<String, Object> request = businessKycDetailsRequest(countryId, stateId);
        ResponseEntity<Map> put = restTemplate.exchange(
                url("/api/v1/business/kyc/details"), HttpMethod.PUT, new HttpEntity<>(request, authHeaders(token)), Map.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put.getBody().get("registeredName")).isEqualTo("Ada Ventures Ltd");

        ResponseEntity<Map> after = restTemplate.exchange(
                url("/api/v1/business/kyc/details"), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(after.getBody().get("cacNumber")).isEqualTo("RC1234567");
    }

    @Test
    void ownerIdentityIsMockVerifiedAndValidated() {
        ResponseEntity<Map> missingBoth = restTemplate.exchange(
                url("/api/v1/kyc/owner-identity"), HttpMethod.PUT,
                new HttpEntity<>(Map.of(), authHeaders(token)), Map.class);
        assertThat(missingBoth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> tooShort = restTemplate.exchange(
                url("/api/v1/kyc/owner-identity"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("bvn", "123"), authHeaders(token)), Map.class);
        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> saved = restTemplate.exchange(
                url("/api/v1/kyc/owner-identity"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("bvn", "12345678901"), authHeaders(token)), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("verified")).isEqualTo(true);
        assertThat(saved.getBody().get("bvn")).isEqualTo("12345678901");
    }

    @Test
    void documentUploadListDownloadAndCrossBusinessScoping() {
        Map<String, Object> uploaded = uploadKycDocument(token, "CAC_CERTIFICATE", "cac.pdf");
        assertThat(uploaded.get("type")).isEqualTo("CAC_CERTIFICATE");

        List<Map<String, Object>> docs = listDocuments(token);
        assertThat(docs).hasSize(1);
        String documentId = (String) docs.get(0).get("id");

        ResponseEntity<byte[]> download = restTemplate.exchange(
                url("/api/v1/kyc/documents/" + documentId + "/download"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(download.getBody())).isEqualTo("test kyc document content");

        String otherBusinessToken = businessSignupAndGetToken("Grace", "Hopper", "SecurePass123!");
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                url("/api/v1/kyc/documents/" + documentId + "/download"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherBusinessToken)), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitRejectsIncompleteKycThenSucceedsOnceComplete() {
        ResponseEntity<Map> noDetails = restTemplate.exchange(
                url("/api/v1/kyc/submit"), HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(noDetails.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        restTemplate.exchange(url("/api/v1/business/kyc/details"), HttpMethod.PUT,
                new HttpEntity<>(businessKycDetailsRequest(countryId, stateId), authHeaders(token)), Map.class);

        ResponseEntity<Map> missingDocs = restTemplate.exchange(
                url("/api/v1/kyc/submit"), HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(missingDocs.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingDocs.getBody().get("errorCode")).isEqualTo(ErrorCode.KYC_DOCUMENTS_MISSING.name());
        assertThat((String) missingDocs.getBody().get("message")).contains("CAC certificate");

        for (String type : List.of("CAC_CERTIFICATE", "MEMORANDUM_AND_ARTICLES", "PROOF_OF_ADDRESS", "DIRECTOR_VALID_ID")) {
            uploadKycDocument(token, type, type.toLowerCase() + ".pdf");
        }

        ResponseEntity<Map> submitted = restTemplate.exchange(
                url("/api/v1/kyc/submit"), HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().get("status")).isEqualTo("PENDING_REVIEW");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listDocuments(String bearerToken) {
        ResponseEntity<List> response = restTemplate.exchange(
                url("/api/v1/kyc/documents"), HttpMethod.GET, new HttpEntity<>(authHeaders(bearerToken)), List.class);
        return response.getBody();
    }
}
