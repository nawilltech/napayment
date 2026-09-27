package ng.com.nawill.pay.payments.collectionaccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin - Collection Account", description = "Nawill Pay's single pooled collection account.")
@RestController
@RequestMapping("/api/v1/collection-account")
public class CollectionAccountController {

    private final CollectionAccountService collectionAccountService;

    public CollectionAccountController(CollectionAccountService collectionAccountService) {
        this.collectionAccountService = collectionAccountService;
    }

    @Operation(summary = "Create the pooled collection account (name resolved via Name Enquiry)")
    @PostMapping
    @PreAuthorize("@auth.can('collection-account:manage')")
    public ResponseEntity<CollectionAccountResponse> create(@Valid @RequestBody CreateCollectionAccountRequest request) {
        CollectionAccount account = collectionAccountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/collection-account/" + account.getId()))
                .body(CollectionAccountResponse.from(account));
    }

    @Operation(summary = "Get the active pooled collection account and its balance")
    @GetMapping
    @PreAuthorize("@auth.can('collection-account:manage')")
    public CollectionAccountResponse get() {
        return CollectionAccountResponse.from(collectionAccountService.getActive());
    }
}
