package ng.com.nawill.pay.onboarding.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import ng.com.nawill.pay.payments.transfer.TransactionPinGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-Auth-2: sets/changes the transaction PIN and implements
 * {@link TransactionPinGateway} for {@code payments.transfer.TransferService}
 * to verify one before money moves (doc 2 §7 ADR-12's cross-module ownership
 * pattern). The PIN is stored and hashed exactly like the password
 * ({@code PasswordEncoder}, on {@code User} directly) but is rate-limited far
 * more strictly - see {@link TransactionPinAttemptService}'s javadoc.
 */
@Service
@Transactional
public class TransactionPinService implements TransactionPinGateway {

    private static final Logger log = LoggerFactory.getLogger(TransactionPinService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionPinAttemptService transactionPinAttemptService;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;

    public TransactionPinService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  TransactionPinAttemptService transactionPinAttemptService,
                                  SecurityAuditService securityAuditService,
                                  CurrentUserResolver currentUserResolver) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionPinAttemptService = transactionPinAttemptService;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
    }

    public void setOrChangePin(SetTransactionPinRequest request) {
        if (!request.pin().equals(request.confirmPin())) {
            throw new ApiException(ErrorCode.PIN_MISMATCH);
        }
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUser.userId()));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INCORRECT_CURRENT_PASSWORD);
        }
        if (user.hasPinSet()) {
            if (request.currentPin() == null || !passwordEncoder.matches(request.currentPin(), user.getPinHash())) {
                throw new ApiException(ErrorCode.INCORRECT_CURRENT_PIN);
            }
        }

        user.updatePinHash(passwordEncoder.encode(request.pin()));
        userRepository.save(user);
        transactionPinAttemptService.clear(user.getId().toString());
        log.info("transaction PIN set: userId={}", user.getId());
        securityAuditService.record(AuditEventType.TRANSACTION_PIN_SET, AuditOutcome.SUCCESS, user.getId(),
                user.getBusinessId(), null);
    }

    @Override
    public void verify(UUID userId, String pin) {
        Optional<Duration> locked = transactionPinAttemptService.lockedRemaining(userId.toString());
        if (locked.isPresent()) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, Lockout.minutesRemaining(locked.get()));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        if (!user.hasPinSet()) {
            throw new ApiException(ErrorCode.PIN_NOT_SET);
        }

        if (!passwordEncoder.matches(pin, user.getPinHash())) {
            int attempts = transactionPinAttemptService.recordFailure(userId.toString());
            if (attempts >= transactionPinAttemptService.maxAttempts()) {
                transactionPinAttemptService.lock(userId.toString());
                log.warn("transfer capability locked after {} failed PIN attempts: userId={}", attempts, userId);
                securityAuditService.record(AuditEventType.TRANSACTION_PIN_LOCKED, AuditOutcome.FAILURE, userId,
                        user.getBusinessId(), attempts + " failed PIN attempts");
                throw new ApiException(ErrorCode.ACCOUNT_LOCKED, Lockout.minutesRemaining(transactionPinAttemptService.lockoutDuration()));
            }
            securityAuditService.record(AuditEventType.TRANSACTION_PIN_VERIFICATION_FAILED, AuditOutcome.FAILURE,
                    userId, user.getBusinessId(),
                    "Incorrect transaction PIN (attempt " + attempts + "/" + transactionPinAttemptService.maxAttempts() + ")");
            throw new ApiException(ErrorCode.INVALID_PIN, attempts, transactionPinAttemptService.maxAttempts());
        }

        transactionPinAttemptService.clear(userId.toString());
    }

}
