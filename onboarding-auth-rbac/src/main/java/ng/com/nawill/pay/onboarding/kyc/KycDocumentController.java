package ng.com.nawill.pay.onboarding.kyc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.storage.LoadedFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "KYC")
@RestController
@RequestMapping("/api/v1/kyc/documents")
public class KycDocumentController {

    private final KycDocumentService kycDocumentService;

    public KycDocumentController(KycDocumentService kycDocumentService) {
        this.kycDocumentService = kycDocumentService;
    }

    @Operation(summary = "Upload a KYC document")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public ResponseEntity<KycDocumentResponse> upload(@RequestParam("type") KycDocumentType type,
                                                         @RequestPart("file") MultipartFile file) {
        KycDocument document = kycDocumentService.upload(type, file);
        return ResponseEntity.status(201).body(KycDocumentResponse.from(document));
    }

    @Operation(summary = "List the business's uploaded KYC documents")
    @GetMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public List<KycDocumentResponse> list() {
        return kycDocumentService.list().stream().map(KycDocumentResponse::from).toList();
    }

    @Operation(summary = "Download one of the business's own KYC documents")
    @GetMapping("/{id}/download")
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        LoadedFile file = kycDocumentService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(new InputStreamResource(file.content()));
    }
}
