package ng.com.nawill.pay.referencedata.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import ng.com.nawill.pay.referencedata.dto.CountryResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reference Data")
@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

    private final ReferenceDataService referenceDataService;

    public CountryController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Operation(summary = "List countries")
    @GetMapping
    public PageResponse<CountryResponse> listCountries(@RequestParam(required = false) String term,
                                                         @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                         @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                referenceDataService.listCountries(term, PageRequest.of(page, size)).map(CountryResponse::from));
    }

    @Operation(summary = "Get a country")
    @GetMapping("/{id}")
    public CountryResponse getCountry(@PathVariable UUID id) {
        return CountryResponse.from(referenceDataService.getCountry(id));
    }
}
