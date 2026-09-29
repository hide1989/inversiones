package co.com.david.inversiones.model.exception;

/** Raised when a ticker symbol does not match the expected market symbol format. */
public class InvalidTickerException extends DomainException {

    public InvalidTickerException(String candidate) {
        super("Not a valid ticker symbol: '%s'".formatted(candidate));
    }
}
