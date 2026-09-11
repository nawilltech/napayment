package ng.com.nawill.pay.onboarding.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * Dispute/refund/support routing for a business (Settings -> Contact). One
 * row per business. Email lists are stored as a single comma-separated
 * column (see the migration comment) rather than a native array type.
 */
@Entity
@Table(name = "business_contact_settings")
public class BusinessContactSettings extends BaseEntity {

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(name = "dispute_emails", nullable = false, columnDefinition = "text")
    private String disputeEmailsCsv = "";

    @Column(name = "refund_emails", nullable = false, columnDefinition = "text")
    private String refundEmailsCsv = "";

    @Column(name = "support_email", length = 128)
    private String supportEmail;

    @Column(name = "general_email", nullable = false, length = 128)
    private String generalEmail;

    protected BusinessContactSettings() {
    }

    public BusinessContactSettings(UUID businessId) {
        this.businessId = businessId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public List<String> getDisputeEmails() {
        return toList(disputeEmailsCsv);
    }

    public List<String> getRefundEmails() {
        return toList(refundEmailsCsv);
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getGeneralEmail() {
        return generalEmail;
    }

    public void update(List<String> disputeEmails, List<String> refundEmails, String supportEmail, String generalEmail) {
        this.disputeEmailsCsv = toCsv(disputeEmails);
        this.refundEmailsCsv = toCsv(refundEmails);
        this.supportEmail = (supportEmail == null || supportEmail.isBlank()) ? null : supportEmail;
        this.generalEmail = generalEmail;
    }

    private static String toCsv(List<String> emails) {
        return emails == null ? "" : String.join(",", emails);
    }

    private static List<String> toList(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }
}
