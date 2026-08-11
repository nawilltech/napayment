package ng.com.nawill.pay.referencedata.controller;

import java.util.List;
import ng.com.nawill.pay.referencedata.dto.BankResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banks")
public class BankController {

    private final ReferenceDataService referenceDataService;

    public BankController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public List<BankResponse> listBanks() {
        return referenceDataService.listBanks().stream().map(BankResponse::from).toList();
    }
}
