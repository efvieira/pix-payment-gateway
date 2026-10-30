package pixpaymentgateway.charge;

/** Resultado de aplicar uma notificação de pagamento a uma cobrança. */
public sealed interface PaymentOutcome {

    record Confirmed(Charge charge) implements PaymentOutcome {
    }

    /** Mesmo endToEndId recebido de novo: o PSP reenviou o webhook. */
    record Duplicate(Charge charge) implements PaymentOutcome {
    }

    record Rejected(Charge charge, String reason) implements PaymentOutcome {
    }
}
