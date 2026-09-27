package ng.com.nawill.pay.payments.paymentmethod;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.common.audit.AuditRecorder;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.platform.PasswordConfirmation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment method catalogue (FR-Proc-2): create, list, edit, switch on/off
 * platform-wide (password-confirmed, like a processor's platform switch) and
 * delete while unused. Every change is audited (FR-Admin-7).
 */
@Service
@Transactional
public class PaymentMethodService {

    private static final int DEFAULT_DISPLAY_ORDER = 100;

    private final PaymentMethodRepository repository;
    private final PasswordConfirmation passwordConfirmation;
    private final CurrentUserResolver currentUserResolver;
    private final AuditRecorder auditRecorder;

    public PaymentMethodService(PaymentMethodRepository repository, PasswordConfirmation passwordConfirmation,
                                CurrentUserResolver currentUserResolver, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.passwordConfirmation = passwordConfirmation;
        this.currentUserResolver = currentUserResolver;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list() {
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse get(UUID id) {
        return toResponse(require(id));
    }

    public PaymentMethodResponse create(CreatePaymentMethodRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        if (repository.existsByCode(code)) {
            throw new ApiException(ErrorCode.PAYMENT_METHOD_CODE_TAKEN);
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.PAYMENT_METHOD_NAME_TAKEN);
        }
        PaymentMethod method = repository.save(new PaymentMethod(code, name, blankToNull(request.description()),
                request.displayOrder() == null ? DEFAULT_DISPLAY_ORDER : request.displayOrder()));
        audit(AuditEventType.PAYMENT_METHOD_CREATED, method, "name=" + name);
        return toResponse(method);
    }

    public PaymentMethodResponse update(UUID id, UpdatePaymentMethodRequest request) {
        PaymentMethod method = require(id);
        if (request.name() != null) {
            String name = request.name().trim();
            if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ApiException(ErrorCode.PAYMENT_METHOD_NAME_TAKEN);
            }
            method.rename(name);
        }
        if (request.description() != null) {
            method.setDescription(blankToNull(request.description()));
        }
        if (request.displayOrder() != null) {
            method.setDisplayOrder(request.displayOrder());
        }
        audit(AuditEventType.PAYMENT_METHOD_UPDATED, method,
                "name=" + method.getName() + " displayOrder=" + method.getDisplayOrder());
        return toResponse(method);
    }

    /** Platform-wide switch: an inactive method can't be used by any processor for new payments. */
    public PaymentMethodResponse setActive(UUID id, boolean active, String password) {
        PaymentMethod method = require(id);
        passwordConfirmation.confirm(currentUserResolver.requireCurrentUser().userId(), password);
        method.setStatus(active ? EntityStatus.ACTIVE : EntityStatus.INACTIVE);
        audit(active ? AuditEventType.PAYMENT_METHOD_ACTIVATED : AuditEventType.PAYMENT_METHOD_DEACTIVATED, method, null);
        return toResponse(method);
    }

    /** Only while nothing references it - otherwise deactivate. */
    public void delete(UUID id) {
        PaymentMethod method = require(id);
        if (repository.countProcessorsOffering(method.getCode()) > 0
                || repository.countTransactionsUsing(method.getCode()) > 0) {
            throw new ApiException(ErrorCode.PAYMENT_METHOD_IN_USE);
        }
        repository.delete(method);
        audit(AuditEventType.PAYMENT_METHOD_DELETED, method, null);
    }

    /** Every catalogue method by code - for labelling processors' methods in responses. */
    @Transactional(readOnly = true)
    public Map<String, PaymentMethod> byCode() {
        return repository.findAll().stream().collect(Collectors.toMap(PaymentMethod::getCode, Function.identity()));
    }

    /** Normalises codes and fails with PAYMENT_METHOD_NOT_FOUND if any isn't in the catalogue. */
    @Transactional(readOnly = true)
    public List<String> requireCodes(Collection<String> codes) {
        List<String> normalised = codes.stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        normalised.forEach(code -> {
            if (!repository.existsByCode(code)) {
                throw new ApiException(ErrorCode.PAYMENT_METHOD_NOT_FOUND);
            }
        });
        return normalised;
    }

    private PaymentMethod require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_METHOD_NOT_FOUND));
    }

    private PaymentMethodResponse toResponse(PaymentMethod method) {
        return PaymentMethodResponse.from(method, repository.countProcessorsOffering(method.getCode()));
    }

    private void audit(AuditEventType eventType, PaymentMethod method, String detail) {
        String base = "method=" + method.getCode() + " (" + method.getId() + ")";
        auditRecorder.record(eventType, AuditOutcome.SUCCESS, currentUserResolver.requireCurrentUser().userId(), null,
                detail == null ? base : base + " " + detail);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
