package ng.com.nawill.pay.referencedata.dto;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.Country;

public record CountryResponse(UUID id, String name, String iso3, String flagUrl, String currency) {

    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getId(), country.getName(), country.getIso3(),
                country.getFlagUrl(), country.getCurrency());
    }
}
