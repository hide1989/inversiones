package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.model.Quantity;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A trade to register or replace, submitted to {@code POST /api/v1/transactions} or
 * {@code PUT /api/v1/transactions/{id}}.
 *
 * <p>{@code quantity}, {@code unitPrice} and {@code fees} are decimal strings, mirroring
 * {@link MoneyDto}: a browser client's JSON numbers are IEEE doubles before they even leave the
 * client, so accepting a string here — and validating its shape with a regex rather than trusting
 * JSON number parsing — is what keeps the precision guarantee symmetric on both sides of the wire.
 *
 * <p>Bean validation here only checks shape (non-blank, a valid decimal, present). Business
 * validity — an unknown ticker, a future trade date, a sale exceeding the position held — is the
 * domain's job and surfaces as a 400 {@code ProblemDetail} through
 * {@link co.com.david.inversiones.api.error.ApiExceptionHandler}.
 */
public record TransactionRequest(
        @NotBlank(message = "ticker must not be blank") String ticker,
        @NotBlank(message = "brokerName must not be blank") String brokerName,
        @NotNull(message = "type must be BUY or SELL") TransactionType type,
        @NotBlank(message = "quantity is required")
        @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "quantity must be a positive decimal string") String quantity,
        @NotBlank(message = "unitPrice is required")
        @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "unitPrice must be a positive decimal string") String unitPrice,
        @NotNull(message = "currency must be USD or COP") Currency currency,
        @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "fees must be a positive decimal string") String fees,
        @NotNull(message = "tradedAt is required") LocalDate tradedAt) {

    /** Builds the domain {@link Transaction} this request describes, with no id assigned. */
    public Transaction toDomain() {
        return toDomain(null);
    }

    /** Builds the domain {@link Transaction} this request describes, replacing the trade with this id. */
    public Transaction toDomain(Long id) {
        Transaction.Builder builder = Transaction.builder()
                .id(id)
                .ticker(Ticker.of(ticker))
                .brokerName(brokerName)
                .type(type)
                .quantity(Quantity.of(new BigDecimal(quantity)))
                .unitPrice(Money.of(new BigDecimal(unitPrice), currency))
                .tradedAt(tradedAt);
        if (fees != null) {
            builder.fees(Money.of(new BigDecimal(fees), currency));
        }
        return builder.build();
    }
}
