package ng.com.nawill.pay.payments.processor;

import jakarta.validation.constraints.NotBlank;

/** A base64 data URL (PNG, JPEG or WebP, max 100 KB) - see {@link ProcessorLogo}. */
public record SetProcessorLogoRequest(@NotBlank String logo) {
}
