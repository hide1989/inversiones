package co.com.david.inversiones.model;

/**
 * One breached allocation ceiling.
 *
 * @param subject the ticker or sector name that is over its limit
 */
public record DiversificationViolation(DiversificationLimit limit, String subject,
                                       Percentage actualWeight, Percentage allowedWeight) {
}
