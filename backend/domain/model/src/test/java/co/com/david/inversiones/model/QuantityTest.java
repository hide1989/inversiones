package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.InvalidQuantityException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityTest {

    @Test
    void supportsFractionalShares() {
        assertThat(Quantity.of("0.486372").value()).isEqualByComparingTo("0.486372");
    }

    @Test
    void normalizesScaleSoThatEqualQuantitiesAreEqual() {
        assertThat(Quantity.of("3")).isEqualTo(Quantity.of("3.0000000000"));
    }

    @Test
    void addsAndSubtracts() {
        assertThat(Quantity.of("4.5").subtract(Quantity.of("1.5"))).isEqualTo(Quantity.of("3"));
        assertThat(Quantity.of("4.5").add(Quantity.of("0.5"))).isEqualTo(Quantity.of("5"));
    }

    @Test
    void rejectsNegativeQuantities() {
        assertThatThrownBy(() -> Quantity.of("-1")).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void rejectsSubtractionThatWouldGoNegative() {
        assertThatThrownBy(() -> Quantity.of("1").subtract(Quantity.of("2")))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void comparesQuantities() {
        assertThat(Quantity.of("2").isGreaterThan(Quantity.of("1.999"))).isTrue();
        assertThat(Quantity.zero().isZero()).isTrue();
    }
}
