package ng.com.nawill.pay.referencedata.controller;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.referencedata.dto.CountryResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

    private final ReferenceDataService referenceDataService;

    public CountryController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public List<CountryResponse> listCountries() {
        return referenceDataService.listCountries().stream().map(CountryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CountryResponse getCountry(@PathVariable UUID id) {
        return CountryResponse.from(referenceDataService.getCountry(id));
    }
}
