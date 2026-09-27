package ng.com.nawill.pay.onboarding.kyc;

public enum KycDocumentType {
    CAC_CERTIFICATE("CAC certificate"),
    MEMORANDUM_AND_ARTICLES("Memorandum and articles of association"),
    PROOF_OF_ADDRESS("Proof of address"),
    DIRECTOR_VALID_ID("Director's valid ID");

    private final String label;

    KycDocumentType(String label) {
        this.label = label;
    }

    /** Human-readable name used in user-facing messages. */
    public String label() {
        return label;
    }
}
