package ng.com.nawill.pay.referencedata.dto;

import java.util.UUID;
import ng.com.nawill.pay.referencedata.entity.Bank;

public record BankResponse(UUID id, String name, String code) {

    public static BankResponse from(Bank bank) {
        return new BankResponse(bank.getId(), bank.getName(), bank.getCode());
    }
}
