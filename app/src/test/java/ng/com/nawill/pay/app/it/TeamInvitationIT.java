package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-5a: inviting a teammate under a role template, and the "join an
 * existing business" signup variant that accepts the invite - the backend
 * surface that replaces napayment-fe's dev-store invite stand-in (which
 * could only mark an invite accepted, never actually attach a user to the
 * inviting business).
 */
class TeamInvitationIT extends AbstractIntegrationTest {

    @Test
    void createListRevokeAndAcceptJoinsTheInvitingBusiness() {
        Map<String, Object> ownerSignup = businessSignup("Grace", "Hopper", "SecurePass123!");
        String ownerToken = (String) ownerSignup.get("accessToken");
        String businessId = (String) ownerSignup.get("businessId");

        ResponseEntity<Map> created = restTemplate.exchange(
                url("/api/v1/team/invitations"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "teammate@example.com", "roleTemplate", "ACCOUNT_OFFICER"),
                        authHeaders(ownerToken)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String inviteUrl = (String) created.getBody().get("inviteUrl");
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        List<Map<String, Object>> listed = getPagedContentOrList("/api/v1/team/invitations", ownerToken);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("status")).isEqualTo("PENDING");

        ResponseEntity<Map> accepted = restTemplate.exchange(
                url("/api/v1/auth/signup/accept-invite"), HttpMethod.POST,
                new HttpEntity<>(Map.of("token", token, "firstName", "Team", "lastName", "Mate",
                        "phoneNo", "08099999999", "password", "SecurePass123!")),
                Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accepted.getBody().get("businessId")).isEqualTo(businessId);

        ResponseEntity<Map> reused = restTemplate.exchange(
                url("/api/v1/auth/signup/accept-invite"), HttpMethod.POST,
                new HttpEntity<>(Map.of("token", token, "firstName", "X", "lastName", "Y",
                        "phoneNo", "08088888888", "password", "SecurePass123!")),
                Map.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void revokeMarksInvitationRevoked() {
        String ownerToken = businessSignupAndGetToken("Ada", "Lovelace", "SecurePass123!");

        ResponseEntity<Map> created = restTemplate.exchange(
                url("/api/v1/team/invitations"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "revoke-me@example.com", "roleTemplate", "DEVELOPER"),
                        authHeaders(ownerToken)),
                Map.class);
        String invitationId = (String) created.getBody().get("id");

        ResponseEntity<Map> revoked = restTemplate.exchange(
                url("/api/v1/team/invitations/" + invitationId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)), Map.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revoked.getBody().get("ok")).isEqualTo(true);

        List<Map<String, Object>> listed = getPagedContentOrList("/api/v1/team/invitations", ownerToken);
        assertThat(listed.get(0).get("status")).isEqualTo("REVOKED");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentOrList(String path, String token) {
        ResponseEntity<List> response = restTemplate.exchange(
                url(path), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), List.class);
        return response.getBody();
    }
}
