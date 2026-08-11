package ng.com.nawill.pay.referencedata.controller;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.referencedata.dto.AdminDivisionResponse;
import ng.com.nawill.pay.referencedata.service.ReferenceDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AdminDivisionController {

    private final ReferenceDataService referenceDataService;

    public AdminDivisionController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/countries/{countryId}/states")
    public List<AdminDivisionResponse> listDivisions(@PathVariable UUID countryId,
                                                       @RequestParam(required = false) Integer level) {
        return referenceDataService.listDivisions(countryId, level).stream()
                .map(AdminDivisionResponse::from).toList();
    }

    @GetMapping("/states/{parentId}/children")
    public List<AdminDivisionResponse> listChildren(@PathVariable UUID parentId) {
        return referenceDataService.listChildren(parentId).stream().map(AdminDivisionResponse::from).toList();
    }
}
