package ng.com.nawill.pay.payments.paymentmethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    /** The catalogue in display order. */
    List<PaymentMethod> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<PaymentMethod> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** How many processors list this method (offered or retired). */
    @Query("SELECT COUNT(m) FROM PaymentProcessorMethod m WHERE m.method = :code")
    long countProcessorsOffering(@Param("code") String code);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.paymentMethod = :code")
    long countTransactionsUsing(@Param("code") String code);
}
