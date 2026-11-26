package ba.slotbook.user;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

// Lozinka koja prolazi ProvjeraLozinke. Poruka je ona koju vrati provjera, pa korisnik zna šta da promijeni.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JakaLozinka.Validator.class)
public @interface JakaLozinka {

	String message() default "Lozinka nije dovoljno jaka.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<JakaLozinka, String> {

		@Override
		public boolean isValid(String lozinka, ConstraintValidatorContext context) {
			// Praznu lozinku javlja @NotBlank, s porukom "Unesi lozinku."
			if (lozinka == null || lozinka.isBlank()) {
				return true;
			}
			String poruka = ProvjeraLozinke.problem(lozinka);
			if (poruka == null) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(poruka).addConstraintViolation();
			return false;
		}

	}

}
