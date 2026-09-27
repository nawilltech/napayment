package ng.com.nawill.pay.payments.transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions", description = "Transactions, history and analytics.")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Record a collection transaction")
    @PostMapping
    @Idempotent
    @PreAuthorize("@auth.can('transactions:create')")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request,
                                                        HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        Transaction transaction = transactionService.create(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.getId()))
                .body(TransactionResponse.from(transaction));
    }

    @Operation(summary = "List transactions with filters")
    @GetMapping
    @PreAuthorize("@auth.can('transactions:read')")
    public PageResponse<TransactionResponse> list(@RequestParam(required = false) String term,
                                                    @RequestParam(required = false) TransactionStatus status,
                                                    @RequestParam(required = false) TransactionType type,
                                                    @RequestParam(required = false) UUID virtualAccountId,
                                                    @RequestParam(required = false) UUID businessId,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
                                                    @RequestParam(required = false) BigInteger minAmount,
                                                    @RequestParam(required = false) BigInteger maxAmount,
                                                    @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                    @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        TransactionFilter filter = new TransactionFilter(fromDate, toDate, status, type, term, virtualAccountId,
                businessId, minAmount, maxAmount);
        return PageResponse.from(
                transactionService.list(filter, PageRequest.of(page, size)).map(TransactionResponse::from));
    }

    @Operation(summary = "Get transaction totals and trends")
    @GetMapping("/analytics")
    @PreAuthorize("@auth.can('transactions:read')")
    public TransactionAnalyticsResponse analytics(@RequestParam(required = false) String term,
                                                     @RequestParam(required = false) TransactionStatus status,
                                                     @RequestParam(required = false) TransactionType type,
                                                     @RequestParam(required = false) UUID virtualAccountId,
                                                     @RequestParam(required = false) UUID businessId,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
                                                     @RequestParam(required = false) BigInteger minAmount,
                                                     @RequestParam(required = false) BigInteger maxAmount) {
        TransactionFilter filter = new TransactionFilter(fromDate, toDate, status, type, term, virtualAccountId,
                businessId, minAmount, maxAmount);
        return transactionService.analyze(filter);
    }

    @Operation(summary = "Get a transaction")
    @GetMapping("/{id}")
    @PreAuthorize("@auth.can('transactions:read')")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.from(transactionService.get(id));
    }
}
