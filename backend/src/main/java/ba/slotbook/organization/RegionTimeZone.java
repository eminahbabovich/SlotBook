package ba.slotbook.organization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

// Samo IANA zone (Europe/Sarajevo). ZoneId.of bi prihvatio i "+01:00", ali fiksni pomak ne zna
// za ljetno vrijeme, pa bi termini od kraja marta do kraja oktobra bili pomjereni za sat.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegionTimeZone.Validator.class)
public @interface RegionTimeZone {

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<RegionTimeZone, String> {

		@Override
		public boolean isValid(String vrijednost, ConstraintValidatorContext context) {
			return vrijednost == null || ZoneId.getAvailableZoneIds().contains(vrijednost);
		}

	}

}
