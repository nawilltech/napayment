package ng.com.nawill.pay.onboarding.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * One allowed IP/CIDR entry for an API key (doc 3 §2.3(b)/§2.5, FR-9). An
 * empty whitelist for a key means unrestricted - a deliberate onboarding-
 * friendliness default (ADR-8, doc 2 §7) rather than deny-by-default.
 */
@Entity
@Table(name = "api_key_ip_whitelist")
public class ApiKeyIpWhitelist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKeyCredential apiKey;

    @Column(name = "cidr", nullable = false, length = 64)
    private String cidr;

    protected ApiKeyIpWhitelist() {
    }

    public ApiKeyIpWhitelist(ApiKeyCredential apiKey, String cidr) {
        this.apiKey = apiKey;
        this.cidr = cidr;
    }

    public ApiKeyCredential getApiKey() {
        return apiKey;
    }

    public String getCidr() {
        return cidr;
    }
}
