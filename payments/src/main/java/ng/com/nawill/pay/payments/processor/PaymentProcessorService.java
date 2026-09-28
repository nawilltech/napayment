package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.common.audit.AuditRecorder;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethodService;
import ng.com.nawill.pay.payments.platform.PasswordConfirmation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platform processor catalogue (FR-Proc-1/2) and its platform-wide
 * switches (FR-Admin-5). Every change is audited (FR-Admin-7); activating,
 * deactivating and "for all businesses" also require the staff member's
 * password.
 */
@Service
@Transactional
public class PaymentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorService.class);
    private static final int DEFAULT_PRIORITY = 100;

    private final PaymentProcessorRepository repository;
    private final BusinessPaymentProcessorRepository settingRepository;
    private final PasswordConfirmation passwordConfirmation;
    private final CurrentUserResolver currentUserResolver;
    private final AuditRecorder auditRecorder;
    private final PaymentMethodService methodService;

    public PaymentProcessorService(PaymentProcessorRepository repository,
                                   BusinessPaymentProcessorRepository settingRepository,
                                   PasswordConfirmation passwordConfirmation,
                                   CurrentUserResolver currentUserResolver,
                                   AuditRecorder auditRecorder,
                                   PaymentMethodService methodService) {
        this.repository = repository;
        this.settingRepository = settingRepository;
        this.passwordConfirmation = passwordConfirmation;
        this.currentUserResolver = currentUserResolver;
        this.auditRecorder = auditRecorder;
        this.methodService = methodService;
    }

    public PaymentProcessorResponse create(CreatePaymentProcessorRequest request) {
        String name = request.name().trim();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.PAYMENT_PROCESSOR_NAME_TAKEN);
        }
        if (repository.existsByCode(code)) {
            throw new ApiException(ErrorCode.PAYMENT_PROCESSOR_CODE_TAKEN);
        }
        PaymentProcessor processor = new PaymentProcessor(name, code,
                request.priority() == null ? DEFAULT_PRIORITY : request.priority(),
                request.defaultEnabled() == null || request.defaultEnabled());
        methodService.requireCodes(request.methods()).forEach(processor::enableMethod);
        if (request.logo() != null && !request.logo().isBlank()) {
            processor.setLogo(request.logo());
        }
        processor = repository.save(processor);
        log.info("payment processor created: processorId={} code={}", processor.getId(), code);
        audit(AuditEventType.PAYMENT_PROCESSOR_CREATED, processor, "methods=" + processor.activeMethods());
        return toResponse(processor);
    }

    /** Hides archived processors unless {@code archived} asks for exactly those. */
    @Transactional(readOnly = true)
    public Page<PaymentProcessorResponse> list(String term, boolean archived, Pageable pageable) {
        boolean search = term != null && !term.isBlank();
        Page<PaymentProcessor> page = archived
                ? (search ? repository.findByArchivedAtIsNotNullAndNameContainingIgnoreCase(term.trim(), pageable)
                        : repository.findByArchivedAtIsNotNull(pageable))
                : (search ? repository.findByArchivedAtIsNullAndNameContainingIgnoreCase(term.trim(), pageable)
                        : repository.findByArchivedAtIsNull(pageable));
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentProcessorResponse get(UUID id) {
        return toResponse(require(id));
    }

    public PaymentProcessorResponse update(UUID id, UpdatePaymentProcessorRequest request) {
        PaymentProcessor processor = require(id);
        if (request.name() != null) {
            String name = request.name().trim();
            if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ApiException(ErrorCode.PAYMENT_PROCESSOR_NAME_TAKEN);
            }
            processor.rename(name);
        }
        if (request.priority() != null) {
            processor.setPriority(request.priority());
        }
        audit(AuditEventType.PAYMENT_PROCESSOR_UPDATED, processor,
                "name=" + processor.getName() + " priority=" + processor.getPriority());
        return toResponse(processor);
    }

    /** Sets the logo, or removes it when {@code logo} is null. */
    public PaymentProcessorResponse setLogo(UUID id, String logo) {
        PaymentProcessor processor = require(id);
        processor.setLogo(logo);
        audit(AuditEventType.PAYMENT_PROCESSOR_UPDATED, processor, logo == null ? "logo removed" : "logo updated");
        return toResponse(processor);
    }

    public PaymentProcessorResponse enableMethod(UUID id, String methodCode) {
        PaymentProcessor processor = require(id);
        String method = methodService.requireCodes(List.of(methodCode)).get(0);
        processor.enableMethod(method);
        repository.save(processor);
        audit(AuditEventType.PAYMENT_METHOD_ENABLED, processor, "method=" + method);
        return toResponse(processor);
    }

    public PaymentProcessorResponse disableMethod(UUID id, String methodCode) {
        PaymentProcessor processor = require(id);
        String method = methodService.requireCodes(List.of(methodCode)).get(0);
        if (!processor.disableMethod(method)) {
            throw new ApiException(ErrorCode.PAYMENT_METHOD_NOT_OFFERED, methodService.byCode().get(method).getName());
        }
        audit(AuditEventType.PAYMENT_METHOD_DISABLED, processor, "method=" + method);
        return toResponse(processor);
    }

    /** Platform switch: INACTIVE stops every business using it; business settings are kept. */
    public PaymentProcessorResponse setActive(UUID id, boolean active, String password) {
        PaymentProcessor processor = require(id);
        if (active && processor.isArchived()) {
            throw new ApiException(ErrorCode.PAYMENT_PROCESSOR_ARCHIVED);
        }
        confirmPassword(password);
        processor.setStatus(active ? EntityStatus.ACTIVE : EntityStatus.INACTIVE);
        audit(active ? AuditEventType.PAYMENT_PROCESSOR_ACTIVATED : AuditEventType.PAYMENT_PROCESSOR_DEACTIVATED,
                processor, null);
        return toResponse(processor);
    }

    /** Sets the default for every business and clears their own settings, so "all" means all. */
    public ForAllBusinessesResponse setForAllBusinesses(UUID id, boolean enabled, String password) {
        PaymentProcessor processor = require(id);
        confirmPassword(password);
        processor.setDefaultEnabled(enabled);
        int cleared = settingRepository.deleteAllForProcessor(id);
        log.info("payment processor {} for all businesses: processorId={} clearedSettings={}",
                enabled ? "enabled" : "disabled", id, cleared);
        audit(enabled ? AuditEventType.PAYMENT_PROCESSOR_ENABLED_FOR_ALL : AuditEventType.PAYMENT_PROCESSOR_DISABLED_FOR_ALL,
                processor, "clearedBusinessSettings=" + cleared);
        return new ForAllBusinessesResponse(toResponse(processor), cleared);
    }

    /**
     * Soft delete (never a hard delete): deactivates and hides it; history
     * still resolves and it can be restored. Its business settings are kept.
     */
    public PaymentProcessorResponse archive(UUID id, String password) {
        PaymentProcessor processor = require(id);
        confirmPassword(password);
        processor.archive();
        audit(AuditEventType.PAYMENT_PROCESSOR_ARCHIVED, processor, null);
        return toResponse(processor);
    }

    /** Back in the lists, still inactive until someone reactivates it. */
    public PaymentProcessorResponse restore(UUID id) {
        PaymentProcessor processor = require(id);
        processor.restore();
        audit(AuditEventType.PAYMENT_PROCESSOR_RESTORED, processor, null);
        return toResponse(processor);
    }

    PaymentProcessor require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_PROCESSOR_NOT_FOUND));
    }

    private void confirmPassword(String password) {
        passwordConfirmation.confirm(currentUserResolver.requireCurrentUser().userId(), password);
    }

    private PaymentProcessorResponse toResponse(PaymentProcessor processor) {
        return PaymentProcessorResponse.from(processor,
                settingRepository.countByProcessorIdAndEnabled(processor.getId(), true),
                settingRepository.countByProcessorIdAndEnabled(processor.getId(), false),
                methodService.byCode());
    }

    private void audit(AuditEventType eventType, PaymentProcessor processor, String detail) {
        String base = "processor=" + processor.getCode() + " (" + processor.getId() + ")";
        auditRecorder.record(eventType, AuditOutcome.SUCCESS, currentUserResolver.requireCurrentUser().userId(), null,
                detail == null ? base : base + " " + detail);
    }
}
