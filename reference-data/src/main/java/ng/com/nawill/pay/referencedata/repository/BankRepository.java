package ng.com.nawill.pay.referencedata.repository;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, UUID> {
}
