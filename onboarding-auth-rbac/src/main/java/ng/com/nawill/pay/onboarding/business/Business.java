package ng.com.nawill.pay.onboarding.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

@Entity
@Table(name = "business")
public class Business extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "cac_number", length = 32)
    private String cacNumber;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    protected Business() {
    }

    public Business(String name, String cacNumber, UUID ownerId) {
        this.name = name;
        this.cacNumber = cacNumber;
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public String getCacNumber() {
        return cacNumber;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }
}
