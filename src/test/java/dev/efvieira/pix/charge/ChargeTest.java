package dev.efvieira.pix.charge;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeTest {

    private final Instant now = Instant.parse("2026-01-10T12:00:00Z");

    private Charge charge() {
        return new Charge("tx1", new BigDecimal("25.00"), "Pedido 1", null, null, "brcode", now, now.plusSeconds(3600));
    }

    @Test
    void confirmsPaymentWithExactAmountBeforeExpiration() {
        var charge = charge();
        assertThat(charge.pay("E1", new BigDecimal("25.0"), now.plusSeconds(10)))
                .isInstanceOf(PaymentOutcome.Confirmed.class);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PAID);
        assertThat(charge.getEndToEndId()).isEqualTo("E1");
    }

    @Test
    void sameEndToEndIdIsDuplicateAndOtherIsRejected() {
        var charge = charge();
        charge.pay("E1", new BigDecimal("25.00"), now);
        assertThat(charge.pay("E1", new BigDecimal("25.00"), now)).isInstanceOf(PaymentOutcome.Duplicate.class);
        assertThat(charge.pay("E2", new BigDecimal("25.00"), now)).isInstanceOf(PaymentOutcome.Rejected.class);
    }

    @Test
    void rejectsWrongAmountAndLatePayment() {
        assertThat(charge().pay("E1", new BigDecimal("24.99"), now)).isInstanceOf(PaymentOutcome.Rejected.class);
        assertThat(charge().pay("E1", new BigDecimal("25.00"), now.plusSeconds(7200)))
                .isInstanceOf(PaymentOutcome.Rejected.class);
    }

    @Test
    void expiresOnlyActiveChargesPastDueDate() {
        var charge = charge();
        assertThat(charge.expireIfDue(now)).isFalse();
        assertThat(charge.expireIfDue(now.plusSeconds(3601))).isTrue();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.EXPIRED);
        assertThat(charge.pay("E1", new BigDecimal("25.00"), now)).isInstanceOf(PaymentOutcome.Rejected.class);
    }

    @Test
    void removesOnlyActiveCharges() {
        var charge = charge();
        charge.remove();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REMOVED);
        assertThatThrownBy(charge::remove).isInstanceOf(IllegalStateException.class);
    }
}
