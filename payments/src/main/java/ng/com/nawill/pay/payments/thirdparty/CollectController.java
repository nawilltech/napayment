package ng.com.nawill.pay.payments.thirdparty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import ng.com.nawill.pay.payments.processor.PaymentProcessor;
import ng.com.nawill.pay.payments.processor.PaymentProcessorRepository;
import ng.com.nawill.pay.payments.transaction.CreateTransactionRequest;
import ng.com.nawill.pay.payments.transaction.Transaction;
import ng.com.nawill.pay.payments.transaction.TransactionResponse;
import ng.com.nawill.pay.payments.transaction.TransactionService;
import ng.com.nawill.pay.payments.transaction.TransactionType;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Third-party collection via a business's API key (FR-9, doc 3 §2.5) -
 * authenticated by {@code ApiKeyAuthenticationFilter}, not a JWT. A thin
 * wrapper: it resolves the caller's own virtual account and an active
 * payment processor, then delegates to the existing
 * {@link TransactionService}, so it goes through the exact same ledger
 * effect, locking, and collection-account mirroring as a JWT-authenticated
 * transaction - no duplicated business logic.
 */
@Tag(name = "Server-to-Server (API Key)", description = "Endpoints for a business's own servers, signed with its API key.")
@RestController
@RequestMapping("/api/v1/collect")
public class CollectController {

    private final TransactionService transactionService;
    private final VirtualAccountQueryService virtualAccountQueryService;
    private final PaymentProcessorRepository paymentProcessorRepository;

    public CollectController(TransactionService transactionService, VirtualAccountQueryService virtualAccountQueryService,
                              PaymentProcessorRepository paymentProcessorRepository) {
        this.transactionService = transactionService;
        this.virtualAccountQueryService = virtualAccountQueryService;
        this.paymentProcessorRepository = paymentProcessorRepository;
    }

    @Operation(summary = "Record a collection into the business's virtual account")
    @PostMapping
    @Idempotent
    @PreAuthorize("@auth.can('collect:create')")
    public ResponseEntity<TransactionResponse> collect(@Valid @RequestBody CollectRequest request,
                                                         HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        VirtualAccount virtualAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();
        PaymentProcessor processor = paymentProcessorRepository.findFirstByStatus(EntityStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active payment processor configured"));

        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                virtualAccount.getId(), processor.getId(), TransactionType.CREDIT, request.amount());
        Transaction transaction = transactionService.create(createRequest, idempotencyKey);

        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.getId()))
                .body(TransactionResponse.from(transaction));
    }
}
