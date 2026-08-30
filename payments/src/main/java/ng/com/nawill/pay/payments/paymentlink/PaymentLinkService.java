package ng.com.nawill.pay.payments.paymentlink;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.processor.PaymentProcessor;
import ng.com.nawill.pay.payments.processor.PaymentProcessorRepository;
import ng.com.nawill.pay.payments.transaction.CreateTransactionRequest;
import ng.com.nawill.pay.payments.transaction.Transaction;
import ng.com.nawill.pay.payments.transaction.TransactionService;
import ng.com.nawill.pay.payments.transaction.TransactionStatus;
import ng.com.nawill.pay.payments.transaction.TransactionType;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment links (FR-14, doc 4 §C.9): a business-created, shareable payment
 * intent, permanent or time-bound/single-use. The short code doubles as the
 * short URL - this IS the URL shortener for the platform, not a separate
 * general-purpose service, since nothing else needs one.
 */
@Service
@Transactional
public class PaymentLinkService {

    private static final int MAX_SHORT_CODE_ATTEMPTS = 5;
    private static final Duration DEFAULT_TEMPORARY_TTL = Duration.ofHours(24);
    private static final String SHORT_CODE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 8;

    private final PaymentLinkRepository paymentLinkRepository;
    private final VirtualAccountQueryService virtualAccountQueryService;
    private final PaymentProcessorRepository paymentProcessorRepository;
    private final TransactionService transactionService;
    private final CurrentUserResolver currentUserResolver;
    private final SecureRandom random = new SecureRandom();

    public PaymentLinkService(PaymentLinkRepository paymentLinkRepository,
                               VirtualAccountQueryService virtualAccountQueryService,
                               PaymentProcessorRepository paymentProcessorRepository,
                               TransactionService transactionService, CurrentUserResolver currentUserResolver) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.virtualAccountQueryService = virtualAccountQueryService;
        this.paymentProcessorRepository = paymentProcessorRepository;
        this.transactionService = transactionService;
        this.currentUserResolver = currentUserResolver;
    }

    public PaymentLink create(CreatePaymentLinkRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        VirtualAccount virtualAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();

        Instant expiresAt = resolveExpiry(request);

        PaymentLink link = new PaymentLink(currentUser.businessId(), virtualAccount, generateUniqueShortCode(),
                request.amount(), virtualAccount.getCurrency(), request.linkType(), expiresAt, request.singleUse());
        return save(link);
    }

    @Transactional(readOnly = true)
    public Page<PaymentLink> listForCallerBusiness(Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return paymentLinkRepository.findByBusinessId(currentUser.businessId(), pageable);
    }

    public void revoke(UUID linkId) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        PaymentLink link = paymentLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + linkId));
        if (!link.isOwnedByBusiness(currentUser.businessId())) {
            throw new BadRequestException("Payment link does not belong to the caller's business");
        }
        link.revoke();
    }

    @Transactional(readOnly = true)
    public PaymentLink resolve(String shortCode) {
        return paymentLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + shortCode));
    }

    /**
     * Locked for the whole eligibility-check -> transact -> redeem sequence
     * (concurrency note in the plan: two simultaneous pay attempts on a
     * single-use link must never both succeed).
     */
    public Transaction pay(String shortCode, PayLinkRequest request, String idempotencyKey) {
        PaymentLink link = paymentLinkRepository.findByShortCodeForUpdate(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + shortCode));

        if (link.isExpired() && link.getLinkStatus() == PaymentLinkStatus.ACTIVE) {
            link.markExpired();
        }
        if (!link.isPayable()) {
            throw new BadRequestException("PAYMENT_LINK_NOT_PAYABLE",
                    "This payment link is " + link.getLinkStatus().name().toLowerCase() + " and cannot be paid");
        }

        BigInteger amount = link.getAmount() != null ? link.getAmount() : request.amount();
        if (amount == null) {
            throw new BadRequestException("AMOUNT_REQUIRED", "This payment link requires the payer to specify an amount");
        }

        PaymentProcessor processor = paymentProcessorRepository.findFirstByStatus(EntityStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active payment processor configured"));

        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                link.getVirtualAccount().getId(), processor.getId(), TransactionType.CREDIT, amount);
        Transaction transaction = transactionService.createForPaymentIntent(createRequest, idempotencyKey);

        if (transaction.getTransactionStatus() == TransactionStatus.PAID && link.isSingleUse()) {
            link.markRedeemed();
        }
        return transaction;
    }

    private Instant resolveExpiry(CreatePaymentLinkRequest request) {
        if (request.linkType() == PaymentLinkType.PERMANENT) {
            if (request.expiresAt() != null) {
                throw new BadRequestException("A permanent payment link cannot have an expiry");
            }
            return null;
        }
        return request.expiresAt() != null ? request.expiresAt() : Instant.now().plus(DEFAULT_TEMPORARY_TTL);
    }

    private PaymentLink save(PaymentLink link) {
        for (int attempt = 1; attempt <= MAX_SHORT_CODE_ATTEMPTS; attempt++) {
            try {
                return paymentLinkRepository.saveAndFlush(link);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_SHORT_CODE_ATTEMPTS) {
                    throw e;
                }
                link.reassignShortCode(generateUniqueShortCode());
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private String generateUniqueShortCode() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
            for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
                sb.append(SHORT_CODE_ALPHABET.charAt(random.nextInt(SHORT_CODE_ALPHABET.length())));
            }
            candidate = sb.toString();
        } while (paymentLinkRepository.existsByShortCode(candidate));
        return candidate;
    }
}
