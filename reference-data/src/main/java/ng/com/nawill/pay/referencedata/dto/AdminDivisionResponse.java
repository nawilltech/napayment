package ng.com.nawill.pay.referencedata.dto;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.AdminDivision;

public record AdminDivisionResponse(UUID id, UUID countryId, String name, Integer level, UUID parentId) {

    public static AdminDivisionResponse from(AdminDivision division) {
        return new AdminDivisionResponse(
                division.getId(),
                division.getCountry().getId(),
                division.getName(),
                division.getLevel(),
                division.getParent() == null ? null : division.getParent().getId());
    }
}
