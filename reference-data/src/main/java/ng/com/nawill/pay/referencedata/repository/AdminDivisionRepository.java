package ng.com.nawill.pay.referencedata.repository;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.AdminDivision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminDivisionRepository extends JpaRepository<AdminDivision, UUID> {

    Page<AdminDivision> findByCountryId(UUID countryId, Pageable pageable);

    Page<AdminDivision> findByCountryIdAndNameContainingIgnoreCase(UUID countryId, String term, Pageable pageable);

    Page<AdminDivision> findByCountryIdAndLevel(UUID countryId, Integer level, Pageable pageable);

    Page<AdminDivision> findByCountryIdAndLevelAndNameContainingIgnoreCase(
            UUID countryId, Integer level, String term, Pageable pageable);

    Page<AdminDivision> findByParentId(UUID parentId, Pageable pageable);

    Page<AdminDivision> findByParentIdAndNameContainingIgnoreCase(UUID parentId, String term, Pageable pageable);
}
