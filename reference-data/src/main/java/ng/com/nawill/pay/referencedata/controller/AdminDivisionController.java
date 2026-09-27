package ng.com.nawill.pay.referencedata.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import ng.com.nawill.pay.referencedata.dto.AdminDivisionResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reference Data", description = "Countries, states and administrative divisions.")
@RestController
@RequestMapping("/api/v1")
public class AdminDivisionController {

    private final ReferenceDataService referenceDataService;

    public AdminDivisionController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Operation(summary = "List a country's states")
    @GetMapping("/countries/{countryId}/states")
    public PageResponse<AdminDivisionResponse> listDivisions(@PathVariable UUID countryId,
                                                               @RequestParam(required = false) Integer level,
                                                               @RequestParam(required = false) String term,
                                                               @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                               @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(referenceDataService.listDivisions(countryId, level, term, PageRequest.of(page, size))
                .map(AdminDivisionResponse::from));
    }

    @Operation(summary = "List the sub-divisions of a state")
    @GetMapping("/states/{parentId}/children")
    public PageResponse<AdminDivisionResponse> listChildren(@PathVariable UUID parentId,
                                                              @RequestParam(required = false) String term,
                                                              @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                              @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(referenceDataService.listChildren(parentId, term, PageRequest.of(page, size))
                .map(AdminDivisionResponse::from));
    }
}
