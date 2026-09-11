package ng.com.nawill.pay.onboarding.email;

/**
 * Outbound transactional email (team invitations today). Send failures never
 * propagate to the caller as a hard error - see {@link SmtpEmailGateway} -
 * email is a delivery convenience, never the system of record (a team
 * invitation is still valid, and its {@code inviteUrl} still shareable
 * manually, even if the email bounces or SMTP is briefly down).
 */
public interface EmailGateway {

    void send(String to, String subject, String body);
}
