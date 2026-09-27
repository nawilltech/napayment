package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.common.audit.AuditRecorder;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethodService;
import ng.com.nawill.pay.payments.platform.BusinessAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A business's own processor settings, managed by platform staff from the business's page (FR-Admin-5). */
@Service
@Transactional
public class BusinessPaymentProcessorService {

    private final BusinessPaymentProcessorRepository settingRepository;
    private final PaymentProcessorService processorService;
    private final ProcessorRouter processorRouter;
    private final BusinessAccess businessAccess;
    private final CurrentUserResolver currentUserResolver;
    private final AuditRecorder auditRecorder;
    private final PaymentMethodService methodService;

    public BusinessPaymentProcessorService(BusinessPaymentProcessorRepository settingRepository,
                                           PaymentProcessorService processorService,
                                           ProcessorRouter processorRouter,
                                           BusinessAccess businessAccess,
                                           CurrentUserResolver currentUserResolver,
                                           AuditRecorder auditRecorder,
                                           PaymentMethodService methodService) {
        this.settingRepository = settingRepository;
        this.processorService = processorService;
        this.processorRouter = processorRouter;
        this.businessAccess = businessAccess;
        this.currentUserResolver = currentUserResolver;
        this.auditRecorder = auditRecorder;
        this.methodService = methodService;
    }

    @Transactional(readOnly = true)
    public List<BusinessPaymentProcessorResponse> list(UUID businessId) {
        businessAccess.requireExists(businessId);
        return processorRouter.availabilityFor(businessId).stream().map(a -> BusinessPaymentProcessorResponse.from(a, methodService.byCode())).toList();
    }

    public BusinessPaymentProcessorResponse set(UUID businessId, UUID processorId, boolean enabled) {
        businessAccess.requireExists(businessId);
        PaymentProcessor processor = processorService.require(processorId);
        settingRepository.findByBusinessIdAndProcessorId(businessId, processorId).ifPresentOrElse(
                setting -> setting.setEnabled(enabled),
                () -> settingRepository.save(new BusinessPaymentProcessor(businessId, processor, enabled)));
        audit(AuditEventType.BUSINESS_PAYMENT_PROCESSOR_SET, businessId, processor, "enabled=" + enabled);
        return current(businessId, processorId);
    }

    /** Removes the business's own setting so it follows the processor's default again. */
    public BusinessPaymentProcessorResponse reset(UUID businessId, UUID processorId) {
        businessAccess.requireExists(businessId);
        PaymentProcessor processor = processorService.require(processorId);
        settingRepository.findByBusinessIdAndProcessorId(businessId, processorId).ifPresent(settingRepository::delete);
        settingRepository.flush();
        audit(AuditEventType.BUSINESS_PAYMENT_PROCESSOR_RESET, businessId, processor, null);
        return current(businessId, processorId);
    }

    private BusinessPaymentProcessorResponse current(UUID businessId, UUID processorId) {
        return processorRouter.availabilityFor(businessId).stream()
                .filter(a -> a.processor().getId().equals(processorId))
                .findFirst()
                .map(a -> BusinessPaymentProcessorResponse.from(a, methodService.byCode()))
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_PROCESSOR_NOT_FOUND));
    }

    private void audit(AuditEventType eventType, UUID businessId, PaymentProcessor processor, String detail) {
        String base = "processor=" + processor.getCode() + " (" + processor.getId() + ")";
        auditRecorder.record(eventType, AuditOutcome.SUCCESS, currentUserResolver.requireCurrentUser().userId(),
                businessId, detail == null ? base : base + " " + detail);
    }
}
