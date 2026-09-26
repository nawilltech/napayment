package ng.com.nawill.pay.onboarding.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PCI DSS 8.3.7: reject a new password that matches any of the account's
 * last {@value #HISTORY_SIZE} passwords. Every comparison goes through
 * {@link PasswordEncoder#matches}, the same as login - a candidate password
 * is never compared to anything but a hash, and history rows store hashes
 * only, never plaintext (same posture as {@code User.passwordHash}).
 * <p>
 * {@code currentPasswordHash} is checked separately from the stored history
 * rather than relied on to already be in it - accounts created before this
 * feature shipped have no history rows yet, so without this an old user
 * could immediately "change" their password back to itself.
 */
@Service
@Transactional
public class PasswordHistoryService {

    static final int HISTORY_SIZE = 4;

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordHistoryService(PasswordHistoryRepository passwordHistoryRepository, PasswordEncoder passwordEncoder) {
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * True if {@code candidatePassword} matches the account's current
     * password or any of its last {@value #HISTORY_SIZE} passwords.
     */
    public boolean isReused(UUID userId, String candidatePassword, String currentPasswordHash) {
        if (currentPasswordHash != null && passwordEncoder.matches(candidatePassword, currentPasswordHash)) {
            return true;
        }
        return recentHashes(userId).stream().anyMatch(hash -> passwordEncoder.matches(candidatePassword, hash));
    }

    /** Records a newly-set password hash and prunes history beyond the last {@value #HISTORY_SIZE}. */
    public void record(UUID userId, String passwordHash) {
        passwordHistoryRepository.save(new PasswordHistory(userId, passwordHash));
        List<PasswordHistory> all = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (all.size() > HISTORY_SIZE) {
            passwordHistoryRepository.deleteAll(all.subList(HISTORY_SIZE, all.size()));
        }
    }

    private List<String> recentHashes(UUID userId) {
        return passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, HISTORY_SIZE))
                .stream().map(PasswordHistory::getPasswordHash).toList();
    }
}
