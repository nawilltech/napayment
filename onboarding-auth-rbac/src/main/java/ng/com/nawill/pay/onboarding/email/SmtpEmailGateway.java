package ng.com.nawill.pay.onboarding.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends via the SMTP relay configured in {@code spring.mail.*} (Gmail SMTP on
 * port 465/implicit TLS today - see {@code application.yml}). Failures are
 * logged, never rethrown: see {@link EmailGateway}'s Javadoc for why a
 * bounced/failed send must not fail the calling operation (e.g. team
 * invitation creation).
 */
@Service
public class SmtpEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailGateway.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailGateway(JavaMailSender mailSender, @Value("${nawill.notifications.email-from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("email sent: to={} subject={}", to, subject);
        } catch (Exception e) {
            log.warn("email send failed, continuing without it: to={} subject={} error={}", to, subject, e.getMessage());
        }
    }
}
