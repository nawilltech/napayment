package ng.com.nawill.pay.payments.processor;

/** Result of switching a processor ON/OFF for all businesses: the business settings it cleared (FR-Admin-5). */
public record ForAllBusinessesResponse(PaymentProcessorResponse processor, int clearedBusinessSettings) {
}
