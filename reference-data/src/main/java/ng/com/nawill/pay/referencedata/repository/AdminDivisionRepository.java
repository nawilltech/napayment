package ng.com.nawill.pay.referencedata.repository;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.AdminDivision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminDivisionRepository extends JpaRepository<AdminDivision, UUID> {

    List<AdminDivision> findByCountryId(UUID countryId);

    List<AdminDivision> findByCountryIdAndLevel(UUID countryId, Integer level);

    List<AdminDivision> findByParentId(UUID parentId);
}
