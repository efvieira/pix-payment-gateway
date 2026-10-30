package dev.efvieira.pix.charge;

import dev.efvieira.pix.brcode.BrCode;
import dev.efvieira.pix.charge.ChargeDtos.CreateChargeRequest;
import dev.efvieira.pix.outbox.OutboxService;
import dev.efvieira.pix.shared.BusinessException;
import dev.efvieira.pix.shared.PixProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private final ChargeRepository charges;
    private final OutboxService outbox;
    private final PixProperties props;
    private final Clock clock;

    public ChargeService(ChargeRepository charges, OutboxService outbox, PixProperties props, Clock clock) {
        this.charges = charges;
        this.outbox = outbox;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Charge create(CreateChargeRequest request) {
        var txid = UUID.randomUUID().toString().replace("-", "");
        var now = clock.instant();
        var ttl = request.expiresInSeconds() != null
                ? Duration.ofSeconds(request.expiresInSeconds())
                : props.defaultExpiration();
        var brCode = BrCode.dynamic(props.locationBaseUrl() + "/" + txid, request.amount(),
                props.merchantName(), props.merchantCity());
        var charge = charges.save(new Charge(txid, request.amount(), request.description(),
                request.payerName(), request.payerDocument(), brCode, now, now.plus(ttl)));
        outbox.append("charge", txid, "pix.charge.created",
                Map.of("txid", txid, "amount", charge.getAmount(), "expiresAt", charge.getExpiresAt()));
        return charge;
    }

    @Transactional(readOnly = true)
    public Charge get(String txid) {
        return charges.findByTxid(txid).orElseThrow(() -> BusinessException.notFound("Cobrança " + txid + " não encontrada"));
    }

    @Transactional
    public Charge remove(String txid) {
        var charge = get(txid);
        try {
            charge.remove();
        } catch (IllegalStateException e) {
            throw BusinessException.unprocessable(e.getMessage());
        }
        outbox.append("charge", txid, "pix.charge.removed", Map.of("txid", txid));
        return charge;
    }

    @Scheduled(fixedDelayString = "${pix.expiration-check-interval:PT30S}")
    @Transactional
    public int expireDueCharges() {
        Instant now = clock.instant();
        int expired = 0;
        for (var charge : charges.findTop100ByStatusAndExpiresAtBefore(ChargeStatus.ACTIVE, now)) {
            if (charge.expireIfDue(now)) {
                outbox.append("charge", charge.getTxid(), "pix.charge.expired", Map.of("txid", charge.getTxid()));
                expired++;
            }
        }
        if (expired > 0) {
            log.info("{} cobranças expiradas", expired);
        }
        return expired;
    }
}
