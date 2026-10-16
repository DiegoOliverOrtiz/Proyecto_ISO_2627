package es.uclm.esibuy.backend.validation;

import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AdultValidator implements ConstraintValidator<Adult, LocalDate> {
	@Override
	public boolean isValid(LocalDate fechaNacimiento, ConstraintValidatorContext context) {
		return fechaNacimiento == null
				|| !fechaNacimiento.isAfter(LocalDate.now(ZoneId.systemDefault()).minusYears(18));
	}
}