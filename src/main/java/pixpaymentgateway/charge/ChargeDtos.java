package pixpaymentgateway.charge;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class ChargeDtos {

    private ChargeDtos() {
    }

    public record CreateChargeRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 140) String description,
            @Size(max = 100) String payerName,
            @Pattern(regexp = "\\d{11}|\\d{14}", message = "CPF (11) ou CNPJ (14) apenas com dígitos") String payerDocument,
            @Min(60) @Max(86_400) Integer expiresInSeconds) {
    }

    public record ChargeResponse(String txid, BigDecimal amount, String description, ChargeStatus status,
                                 String pixCopiaECola, Instant createdAt, Instant expiresAt,
                                 Instant paidAt, String endToEndId) {

        static ChargeResponse from(Charge c) {
            return new ChargeResponse(c.getTxid(), c.getAmount(), c.getDescription(), c.getStatus(),
                    c.getBrCode(), c.getCreatedAt(), c.getExpiresAt(), c.getPaidAt(), c.getEndToEndId());
        }
    }
}
