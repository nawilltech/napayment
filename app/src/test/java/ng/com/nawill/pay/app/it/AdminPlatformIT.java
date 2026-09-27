package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-3 / doc 4 §C.4-C.5: the platform admin surface - cross-business listing,
 * the KYC review decision, reviewer document access and the audit-log viewer.
 * Only platform permissions (always held by SUPERADMIN) reach any of it.
 */
class AdminPlatformIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";

    @Test
    void businessAndIndividualAccountsCannotReachThePlatformSurface() {
        String businessToken = businessSignupAndGetToken("Bola", "Tinubu", PASSWORD);
        String individualToken = signupAndGetToken("Ada", "Obi", PASSWORD);

        for (String token : List.of(businessToken, individualToken)) {
            for (String path : List.of("/api/v1/admin/businesses", "/api/v1/admin/businesses/stats",
                    "/api/v1/admin/audit-logs")) {
                assertThat(get(path, token).getStatusCode()).as(path).isEqualTo(HttpStatus.FORBIDDEN);
            }
        }
    }

    @Test
    void listsBusinessesAcrossThePlatformWithOwnerAndStats() {
        Map<String, Object> signup = businessSignup("Chidi", "Okeke", PASSWORD);
        String businessId = (String) signup.get("businessId");
        String adminToken = superAdminToken();

        ResponseEntity<Map> detail = get("/api/v1/admin/businesses/" + businessId, adminToken);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> summary = summaryOf(detail);
        String name = (String) summary.get("name");
        assertThat(summary.get("ownerName")).isEqualTo("Chidi Okeke");
        assertThat(summary.get("kycStatus")).isEqualTo("NOT_STARTED");

        List<Map<String, Object>> found = getPagedContent("/api/v1/admin/businesses?term=" + name,
                authHeaders(adminToken));
        assertThat(found).extracting(row -> row.get("id")).containsExactly(businessId);

        ResponseEntity<Map> stats = get("/api/v1/admin/businesses/stats", adminToken);
        assertThat(((Number) stats.getBody().get("total")).longValue()).isPositive();
        assertThat((Map<String, Object>) stats.getBody().get("byKycStatus")).containsKeys("NOT_STARTED", "PENDING_REVIEW");
    }

    @Test
    void approvingKycVerifiesTheBusinessIsAuditedAndCannotBeRepeated() {
        Map<String, Object> signup = businessSignup("Ngozi", "Iweala", PASSWORD);
        String businessId = (String) signup.get("businessId");
        submitCompleteKyc((String) signup.get("accessToken"));
        String adminToken = superAdminToken();

        List<Map<String, Object>> queue = getPagedContent("/api/v1/admin/businesses?kycStatus=PENDING_REVIEW&size=100",
                authHeaders(adminToken));
        assertThat(queue).extracting(row -> row.get("id")).contains(businessId);

        ResponseEntity<Map> detail = get("/api/v1/admin/businesses/" + businessId, adminToken);
        assertThat((List<?>) detail.getBody().get("documents")).hasSize(KYC_DOCUMENT_TYPES.size());

        ResponseEntity<Map> approved = post("/api/v1/admin/businesses/" + businessId + "/kyc/approve", null, adminToken);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summaryOf(approved).get("kycStatus")).isEqualTo("VERIFIED");
        assertThat(approved.getBody().get("kycReviewedAt")).isNotNull();

        ResponseEntity<Map> again = post("/api/v1/admin/businesses/" + businessId + "/kyc/approve", null, adminToken);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        List<Map<String, Object>> audit = getPagedContent("/api/v1/admin/audit-logs?businessId=" + businessId,
                authHeaders(adminToken));
        assertThat(audit).extracting(row -> row.get("eventType")).contains("KYC_SUBMITTED", "KYC_APPROVED");
        assertThat(audit.get(0).get("eventType")).as("newest first").isEqualTo("KYC_APPROVED");
    }

    @Test
    void rejectingKycNeedsAReasonAndTheBusinessCanResubmit() {
        Map<String, Object> signup = businessSignup("Femi", "Otedola", PASSWORD);
        String businessId = (String) signup.get("businessId");
        String businessToken = (String) signup.get("accessToken");
        submitCompleteKyc(businessToken);
        String adminToken = superAdminToken();
        String rejectPath = "/api/v1/admin/businesses/" + businessId + "/kyc/reject";

        assertThat(post(rejectPath, Map.of("reason", " "), adminToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> rejected = post(rejectPath, Map.of("reason", "Proof of address is older than 3 months"),
                adminToken);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summaryOf(rejected).get("kycStatus")).isEqualTo("REJECTED");
        assertThat(rejected.getBody().get("kycReviewNote")).isEqualTo("Proof of address is older than 3 months");

        ResponseEntity<Map> resubmitted = restTemplate.exchange(url("/api/v1/kyc/submit"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(businessToken)), Map.class);
        assertThat(resubmitted.getBody().get("status")).isEqualTo("PENDING_REVIEW");

        ResponseEntity<Map> detail = get("/api/v1/admin/businesses/" + businessId, adminToken);
        assertThat(summaryOf(detail).get("kycStatus")).isEqualTo("PENDING_REVIEW");
        assertThat(detail.getBody().get("kycReviewNote")).as("previous decision cleared on resubmission").isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reviewersCanDownloadAnyBusinessesDocumentButBusinessesCannotUseTheReviewPath() {
        Map<String, Object> signup = businessSignup("Tony", "Elumelu", PASSWORD);
        submitCompleteKyc((String) signup.get("accessToken"));
        String adminToken = superAdminToken();

        List<Map<String, Object>> documents = (List<Map<String, Object>>)
                get("/api/v1/admin/businesses/" + signup.get("businessId"), adminToken).getBody().get("documents");
        String downloadPath = "/api/v1/admin/kyc-documents/" + documents.get(0).get("id") + "/download";

        ResponseEntity<byte[]> download = restTemplate.exchange(url(downloadPath), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(download.getBody())).isEqualTo("test kyc document content");

        String otherBusinessToken = businessSignupAndGetToken("Aliko", "Dangote", PASSWORD);
        assertThat(get(downloadPath, otherBusinessToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownBusinessIs404() {
        assertThat(get("/api/v1/admin/businesses/" + UUID.randomUUID(), superAdminToken()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
    }

    private ResponseEntity<Map> post(String path, Object body, String token) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summaryOf(ResponseEntity<Map> detail) {
        return (Map<String, Object>) detail.getBody().get("summary");
    }
}
