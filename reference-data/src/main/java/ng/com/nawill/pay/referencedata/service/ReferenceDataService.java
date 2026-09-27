package ng.com.nawill.pay.referencedata.service;

import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.referencedata.entity.AdminDivision;
import ng.com.nawill.pay.referencedata.entity.Bank;
import ng.com.nawill.pay.referencedata.entity.Country;
import ng.com.nawill.pay.referencedata.repository.AdminDivisionRepository;
import ng.com.nawill.pay.referencedata.repository.BankRepository;
import ng.com.nawill.pay.referencedata.repository.CountryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Page<Country> listCountries(String term, Pageable pageable) {
        return isBlank(term)
                ? countryRepository.findAll(pageable)
                : countryRepository.findByNameContainingIgnoreCase(term.trim(), pageable);
    }

    public Country getCountry(UUID id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.COUNTRY_NOT_FOUND));
    }

    public Page<AdminDivision> listDivisions(UUID countryId, Integer level, String term, Pageable pageable) {
        if (level == null) {
            return isBlank(term)
                    ? adminDivisionRepository.findByCountryId(countryId, pageable)
                    : adminDivisionRepository.findByCountryIdAndNameContainingIgnoreCase(countryId, term.trim(), pageable);
        }
        return isBlank(term)
                ? adminDivisionRepository.findByCountryIdAndLevel(countryId, level, pageable)
                : adminDivisionRepository.findByCountryIdAndLevelAndNameContainingIgnoreCase(
                        countryId, level, term.trim(), pageable);
    }

    public Page<AdminDivision> listChildren(UUID parentId, String term, Pageable pageable) {
        return isBlank(term)
                ? adminDivisionRepository.findByParentId(parentId, pageable)
                : adminDivisionRepository.findByParentIdAndNameContainingIgnoreCase(parentId, term.trim(), pageable);
    }

    public Page<Bank> listBanks(String term, Pageable pageable) {
        if (isBlank(term)) {
            return bankRepository.findAll(pageable);
        }
        String trimmed = term.trim();
        return bankRepository.findByNameContainingIgnoreCaseOrCodeStartingWith(trimmed, trimmed, pageable);
    }

    private static boolean isBlank(String term) {
        return term == null || term.isBlank();
    }
}
