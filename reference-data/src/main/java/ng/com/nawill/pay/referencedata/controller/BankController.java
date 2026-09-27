package ng.com.nawill.pay.referencedata.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import ng.com.nawill.pay.referencedata.dto.BankResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Banks")
@RestController
@RequestMapping("/api/v1/banks")
public class BankController {

    private final ReferenceDataService referenceDataService;

    public BankController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Operation(summary = "List banks A-Z, optionally searched by name or bank code (term)")
    @GetMapping
    public PageResponse<BankResponse> listBanks(
            @Parameter(description = "Case-insensitive fragment of the bank name, or the start of its code, e.g. \"zen\" or \"057\"")
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        Sort byName = Sort.by(Sort.Order.asc("name").ignoreCase());
        return PageResponse.from(referenceDataService.listBanks(term, PageRequest.of(page, size, byName))
                .map(BankResponse::from));
    }
}
