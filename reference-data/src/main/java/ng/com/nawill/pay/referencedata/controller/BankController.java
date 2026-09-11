package ng.com.nawill.pay.referencedata.controller;

import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import ng.com.nawill.pay.referencedata.dto.BankResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banks")
public class BankController {

    private final ReferenceDataService referenceDataService;

    public BankController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public PageResponse<BankResponse> listBanks(@RequestParam(required = false) String term,
                                                 @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                 @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(referenceDataService.listBanks(term, PageRequest.of(page, size)).map(BankResponse::from));
    }
}
