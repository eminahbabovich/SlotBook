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

// Samo zone stvarnih mjesta (Europe/Sarajevo). Fiksni pomak ne zna za ljetno vrijeme, pa bi termini
// od kraja marta do kraja oktobra bili pomjereni za sat. "+01:00" nije na listi IANA zona, ali
// Etc/GMT-1 i UTC jesu, pa ih odbija tek provjera da pravila zone nikad nemaju prelaz.
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
			if (vrijednost == null) {
				return true;
			}
			return ZoneId.getAvailableZoneIds().contains(vrijednost)
					&& !ZoneId.of(vrijednost).getRules().isFixedOffset();
		}

	}

}
