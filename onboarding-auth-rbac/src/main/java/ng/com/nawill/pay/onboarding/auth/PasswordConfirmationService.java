package ng.com.nawill.pay.onboarding.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import ng.com.nawill.pay.payments.platform.PasswordConfirmation;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Implements payments' {@link PasswordConfirmation} (FR-Admin-5). Shares the
 * login attempt counter, so guessing a password here is no easier than at
 * sign-in: failures count toward the same lockout, and a locked account can
 * neither sign in nor confirm.
 */
@Service
public class PasswordConfirmationService implements PasswordConfirmation {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditService securityAuditService;

    public PasswordConfirmationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                       LoginAttemptService loginAttemptService,
                                       SecurityAuditService securityAuditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.securityAuditService = securityAuditService;
    }

    @Override
    public void confirm(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        String email = user.getEmail();
        Optional<Duration> locked = loginAttemptService.lockedRemaining(email);
        if (locked.isPresent()) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, Lockout.minutesRemaining(locked.get()));
        }
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.clear(email);
            return;
        }
        int attempts = loginAttemptService.recordFailure(email);
        securityAuditService.record(AuditEventType.PASSWORD_CONFIRMATION_FAILED, AuditOutcome.FAILURE, userId,
                user.getBusinessId(), "attempt " + attempts + "/" + loginAttemptService.maxAttempts());
        if (attempts >= loginAttemptService.maxAttempts()) {
            loginAttemptService.lock(email);
            securityAuditService.record(AuditEventType.ACCOUNT_LOCKED, AuditOutcome.FAILURE, userId,
                    user.getBusinessId(), "Locked after failed password confirmations");
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, Lockout.minutesRemaining(loginAttemptService.lockoutDuration()));
        }
        throw new ApiException(ErrorCode.PASSWORD_CONFIRMATION_FAILED, attempts, loginAttemptService.maxAttempts());
    }
}
