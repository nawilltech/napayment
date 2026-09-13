package ng.com.nawill.pay.payments.transfer;

/** Preview shown before the sender commits to a transfer - confirm the name before the money moves. */
public record TransferResolveResponse(String displayName, String accountNumberMasked) {
}
