package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.DiversificationViolation;

/** Wire representation of a {@link DiversificationViolation}: one breached allocation ceiling. */
public record DiversificationViolationDto(String limit, String subject, String actualWeight, String allowedWeight) {

    public static DiversificationViolationDto from(DiversificationViolation violation) {
        return new DiversificationViolationDto(
                violation.limit().name(),
                violation.subject(),
                DecimalStrings.of(violation.actualWeight().value()),
                DecimalStrings.of(violation.allowedWeight().value()));
    }
}
