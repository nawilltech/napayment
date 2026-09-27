package ng.com.nawill.pay.referencedata.repository;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.Bank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, UUID> {

    /** Bank picker search: a fragment of the name ("zen") or the start of the CBN/NIP code ("057"). */
    Page<Bank> findByNameContainingIgnoreCaseOrCodeStartingWith(String name, String code, Pageable pageable);
}
