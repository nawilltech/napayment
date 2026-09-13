package ng.com.nawill.pay.payments.transfer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-Auth-1: peer-to-peer transfer between two Nawill virtual accounts. */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /** Confirm-before-send: shows the resolved recipient's masked name before the caller commits to a transfer. */
    @GetMapping("/resolve")
    @PreAuthorize("@auth.can('transfers:create')")
    public TransferResolveResponse resolve(@RequestParam String identifier) {
        return transferService.resolve(identifier);
    }

    @PostMapping
    @Idempotent
    @PreAuthorize("@auth.can('transfers:create')")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request,
                                                       HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        TransferResponse response = transferService.transfer(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
