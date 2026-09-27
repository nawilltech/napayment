package ng.com.nawill.pay.payments.processor;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;

/**
 * A processor logo as stored in {@code payment_processors.logo}: a base64
 * data URL of a PNG, JPEG or WebP image, at most {@link #MAX_KB} KB decoded.
 * SVG is refused - it can carry scripts. Clients downscale before upload.
 */
public final class ProcessorLogo {

    public static final int MAX_KB = 100;
    private static final int MAX_BYTES = MAX_KB * 1024;
    private static final Pattern DATA_URL =
            Pattern.compile("^data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$");

    private ProcessorLogo() {
    }

    /** Returns the logo unchanged if valid; throws {@code INVALID_LOGO} otherwise. */
    public static String validate(String logo) {
        Matcher matcher = DATA_URL.matcher(logo == null ? "" : logo.trim());
        if (!matcher.matches()) {
            throw invalid();
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (decoded.length == 0 || decoded.length > MAX_BYTES) {
            throw invalid();
        }
        return logo.trim();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_LOGO, MAX_KB);
    }
}
