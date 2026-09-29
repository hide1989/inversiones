package co.com.david.inversiones.model.exception;

import java.time.LocalDate;

/** Raised when a trade is dated in the future. */
public class InvalidTradeDateException extends DomainException {

    public InvalidTradeDateException(LocalDate tradedAt, LocalDate today) {
        super("Trade date %s is in the future; today is %s".formatted(tradedAt, today));
    }
}
