package ng.com.nawill.pay.referencedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;

@Entity
@Table(name = "countries")
public class Country extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "iso3", nullable = false, unique = true, length = 3)
    private String iso3;

    @Column(name = "flag_url", length = 512)
    private String flagUrl;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected Country() {
    }

    public Country(String name, String iso3, String flagUrl, String currency) {
        this.name = name;
        this.iso3 = iso3;
        this.flagUrl = flagUrl;
        this.currency = currency;
    }

    public String getName() {
        return name;
    }

    public String getIso3() {
        return iso3;
    }

    public String getFlagUrl() {
        return flagUrl;
    }

    public String getCurrency() {
        return currency;
    }
}
