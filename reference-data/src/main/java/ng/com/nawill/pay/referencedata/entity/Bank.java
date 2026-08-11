package ng.com.nawill.pay.referencedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;

@Entity
@Table(name = "banks")
public class Bank extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    protected Bank() {
    }

    public Bank(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }
}
