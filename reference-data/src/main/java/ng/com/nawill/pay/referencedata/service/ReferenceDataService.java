package ng.com.nawill.pay.referencedata.service;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.referencedata.entity.AdminDivision;
import ng.com.nawill.pay.referencedata.entity.Bank;
import ng.com.nawill.pay.referencedata.entity.Country;
import ng.com.nawill.pay.referencedata.repository.AdminDivisionRepository;
import ng.com.nawill.pay.referencedata.repository.BankRepository;
import ng.com.nawill.pay.referencedata.repository.CountryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final CountryRepository countryRepository;
    private final AdminDivisionRepository adminDivisionRepository;
    private final BankRepository bankRepository;

    public ReferenceDataService(CountryRepository countryRepository,
                                 AdminDivisionRepository adminDivisionRepository,
                                 BankRepository bankRepository) {
        this.countryRepository = countryRepository;
        this.adminDivisionRepository = adminDivisionRepository;
        this.bankRepository = bankRepository;
    }

    public List<Country> listCountries() {
        return countryRepository.findAll();
    }

    public Country getCountry(UUID id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Country not found: " + id));
    }

    public List<AdminDivision> listDivisions(UUID countryId, Integer level) {
        return level == null
                ? adminDivisionRepository.findByCountryId(countryId)
                : adminDivisionRepository.findByCountryIdAndLevel(countryId, level);
    }

    public List<AdminDivision> listChildren(UUID parentId) {
        return adminDivisionRepository.findByParentId(parentId);
    }

    public List<Bank> listBanks() {
        return bankRepository.findAll();
    }
}
