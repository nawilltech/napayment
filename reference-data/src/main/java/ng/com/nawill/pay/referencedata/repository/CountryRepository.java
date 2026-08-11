package ng.com.nawill.pay.referencedata.repository;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, UUID> {
}
