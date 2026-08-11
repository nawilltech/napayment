package ng.com.nawill.pay.onboarding.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * Central identity table for every user type (doc 2 §4.2). businessId is a
 * plain UUID, set after a business is created during business signup - see
 * doc 4 C.1 - since Business.ownerId -> User creates a circular reference
 * that's resolved at the migration level (V0004-V0006), not via a JPA graph.
 * addressId is a bare column with no FK yet - TODO(FR-8): Address/KYC are
 * out of this session's scope.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 64)
    private String firstName;

    @Column(name = "middle_name", length = 64)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 64)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 128)
    private String email;

    @Column(name = "phone_no", nullable = false, unique = true, length = 20)
    private String phoneNo;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "dob")
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 16)
    private UserType userType;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "business_id")
    private UUID businessId;

    protected User() {
    }

    public User(String firstName, String middleName, String lastName, String email, String phoneNo,
                String passwordHash, UserType userType) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNo = phoneNo;
        this.passwordHash = passwordHash;
        this.userType = userType;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public void markVerified() {
        this.verified = true;
    }

    public LocalDate getDob() {
        return dob;
    }

    public UserType getUserType() {
        return userType;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public void assignBusiness(UUID businessId) {
        this.businessId = businessId;
    }
}
