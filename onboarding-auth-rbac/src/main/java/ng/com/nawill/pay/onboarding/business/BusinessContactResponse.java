package ng.com.nawill.pay.onboarding.business;

import java.util.List;

public record BusinessContactResponse(
        List<String> disputeEmails,
        List<String> refundEmails,
        String supportEmail,
        String generalEmail
) {

    public static BusinessContactResponse from(BusinessContactSettings settings) {
        return new BusinessContactResponse(settings.getDisputeEmails(), settings.getRefundEmails(),
                settings.getSupportEmail(), settings.getGeneralEmail());
    }
}
