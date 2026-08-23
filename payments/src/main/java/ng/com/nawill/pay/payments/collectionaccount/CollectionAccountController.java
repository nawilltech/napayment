package ng.com.nawill.pay.payments.collectionaccount;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only (doc 3 §2.5): {@code collection-account:manage} is not
 * assigned to any seeded role, so only SUPERADMIN's blanket permission
 * bypass can reach these endpoints.
 */
@RestController
@RequestMapping("/api/v1/collection-account")
public class CollectionAccountController {

    private final CollectionAccountService collectionAccountService;

    public CollectionAccountController(CollectionAccountService collectionAccountService) {
        this.collectionAccountService = collectionAccountService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('collection-account:manage')")
    public ResponseEntity<CollectionAccountResponse> create(@Valid @RequestBody CreateCollectionAccountRequest request) {
        CollectionAccount account = collectionAccountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/collection-account/" + account.getId()))
                .body(CollectionAccountResponse.from(account));
    }

    @GetMapping
    @PreAuthorize("@auth.can('collection-account:manage')")
    public CollectionAccountResponse get() {
        return CollectionAccountResponse.from(collectionAccountService.getActive());
    }
}
