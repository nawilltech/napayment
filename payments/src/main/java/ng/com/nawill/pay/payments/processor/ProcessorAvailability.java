package ng.com.nawill.pay.payments.processor;

/**
 * Whether one processor is available to one account, and why (FR-Proc-3).
 * {@code businessSetting} is the business's own ON/OFF, null when it follows
 * the processor's default (always null for individuals).
 */
public record ProcessorAvailability(PaymentProcessor processor, Boolean businessSetting, boolean available,
                                    Source source) {

    public enum Source { PROCESSOR_INACTIVE, BUSINESS_SETTING, PLATFORM_DEFAULT }

    static ProcessorAvailability of(PaymentProcessor processor, Boolean businessSetting) {
        if (!processor.isActive()) {
            return new ProcessorAvailability(processor, businessSetting, false, Source.PROCESSOR_INACTIVE);
        }
        if (businessSetting != null) {
            return new ProcessorAvailability(processor, businessSetting, businessSetting, Source.BUSINESS_SETTING);
        }
        return new ProcessorAvailability(processor, null, processor.isDefaultEnabled(), Source.PLATFORM_DEFAULT);
    }
}
