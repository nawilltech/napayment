package ng.com.nawill.pay.onboarding.bootstrap;

import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import ng.com.nawill.pay.onboarding.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the platform SUPERADMIN at startup (doc 1 FR-5: "not a self-service-
 * creatable role"). Idempotent - a no-op once the account exists. Credentials
 * come from env vars, never a hardcoded default password (doc 3 §2.3).
 */
@Component
public class SuperAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String superAdminEmail;
    private final String superAdminPassword;

    public SuperAdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             @Value("${nawill.auth.superadmin.email}") String superAdminEmail,
                             @Value("${nawill.auth.superadmin.password}") String superAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.superAdminEmail = superAdminEmail;
        this.superAdminPassword = superAdminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(superAdminEmail)) {
            return;
        }
        User superAdmin = new User("Nawill", null, "SuperAdmin", superAdminEmail, "0000000000",
                passwordEncoder.encode(superAdminPassword), UserType.SUPERADMIN);
        superAdmin.markVerified();
        userRepository.save(superAdmin);
        log.info("seeded platform SUPERADMIN account: userId={}", superAdmin.getId());
    }
}
