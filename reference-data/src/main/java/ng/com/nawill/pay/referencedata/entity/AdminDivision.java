package ng.com.nawill.pay.referencedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * Nigeria's administrative hierarchy modelled 3 levels deep - self-
 * referencing so other countries can reuse the same table with their own
 * hierarchy depth (doc 2 §4.2).
 */
@Entity
@Table(name = "states")
public class AdminDivision extends BaseEntity {

    public static final int LEVEL_STATE = 1;
    public static final int LEVEL_LGA = 2;
    public static final int LEVEL_WARD = 3;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "level", nullable = false)
    private Integer level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AdminDivision parent;

    protected AdminDivision() {
    }

    public AdminDivision(Country country, String name, Integer level, AdminDivision parent) {
        this.country = country;
        this.name = name;
        this.level = level;
        this.parent = parent;
    }

    public Country getCountry() {
        return country;
    }

    public String getName() {
        return name;
    }

    public Integer getLevel() {
        return level;
    }

    public AdminDivision getParent() {
        return parent;
    }
}
