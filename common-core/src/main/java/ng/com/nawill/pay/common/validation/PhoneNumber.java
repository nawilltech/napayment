package ng.com.nawill.pay.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default "Must be a valid phone number, digits only "
            + "(e.g. 08012345678, or +2348012345678 for other regions)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
