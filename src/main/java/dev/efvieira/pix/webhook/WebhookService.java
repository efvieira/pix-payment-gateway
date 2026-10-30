package dev.efvieira.pix.webhook;

import dev.efvieira.pix.charge.ChargeRepository;
import dev.efvieira.pix.charge.PaymentOutcome;
import dev.efvieira.pix.outbox.OutboxService;
import dev.efvieira.pix.shared.BusinessException;
import dev.efvieira.pix.shared.PixProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Recebe as notificações de Pix do PSP no formato do webhook da API Pix do Bacen.
 * A autenticidade é verificada por HMAC-SHA256 do corpo bruto; o processamento é
 * idempotente pelo endToEndId, então reenvios do PSP não geram efeito duplicado.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public record PixNotification(String endToEndId, String txid, BigDecimal valor, Instant horario) {
    }

    public record WebhookPayload(List<PixNotification> pix) {
    }

    public record WebhookResult(int confirmed, int duplicated, int rejected, int unknown) {
    }

    private final ChargeRepository charges;
    private final OutboxService outbox;
    private final JsonMapper json;
    private final byte[] secret;

    public WebhookService(ChargeRepository charges, OutboxService outbox, JsonMapper json, PixProperties props) {
        this.charges = charges;
        this.outbox = outbox;
        this.json = json;
        this.secret = props.webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public WebhookResult process(String rawBody, String signature) {
        if (signature == null || !MessageDigest.isEqual(sign(rawBody).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida");
        }
        var payload = json.readValue(rawBody, WebhookPayload.class);
        int confirmed = 0, duplicated = 0, rejected = 0, unknown = 0;
        for (var pix : payload.pix()) {
            var charge = charges.findByTxid(pix.txid());
            if (charge.isEmpty()) {
                log.warn("Pix {} recebido para txid desconhecido {}", pix.endToEndId(), pix.txid());
                unknown++;
                continue;
            }
            switch (charge.get().pay(pix.endToEndId(), pix.valor(), pix.horario())) {
                case PaymentOutcome.Confirmed(var c) -> {
                    outbox.append("charge", c.getTxid(), "pix.charge.paid", Map.of(
                            "txid", c.getTxid(), "endToEndId", pix.endToEndId(),
                            "amount", pix.valor(), "paidAt", pix.horario()));
                    confirmed++;
                }
                case PaymentOutcome.Duplicate _ -> duplicated++;
                case PaymentOutcome.Rejected(var c, var reason) -> {
                    outbox.append("charge", c.getTxid(), "pix.payment.rejected", Map.of(
                            "txid", c.getTxid(), "endToEndId", pix.endToEndId(),
                            "amount", pix.valor(), "reason", reason));
                    rejected++;
                }
            }
        }
        return new WebhookResult(confirmed, duplicated, rejected, unknown);
    }

    public String sign(String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
