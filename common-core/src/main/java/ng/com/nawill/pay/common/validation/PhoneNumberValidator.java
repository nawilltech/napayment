package ng.com.nawill.pay.common.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Rejects anything that isn't digits (with an optional leading {@code +})
 * outright, then defers to Google's libphonenumber for real validity - a
 * regex alone can confirm shape but not whether a number falls within an
 * actually-allocated range for its region, and that data changes over time
 * (new prefixes, reallocated ranges) in a way a hand-maintained pattern
 * can't track. Defaults to Nigeria ({@code NG}) when the value has no
 * explicit {@code +<country code>} prefix, since every phone number in this
 * app is Nigerian-first; an explicit {@code +234...} (or any other country)
 * is still parsed correctly regardless of the default region.
 */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\+?\\d+$");
    private static final String DEFAULT_REGION = "NG";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!DIGITS_ONLY.matcher(value).matches()) {
            return false;
        }
        try {
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(value, DEFAULT_REGION);
            return phoneNumberUtil.isValidNumber(parsed);
        } catch (NumberParseException e) {
            return false;
        }
    }
}
