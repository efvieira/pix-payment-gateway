package pixpaymentgateway.webhook;

import pixpaymentgateway.charge.ChargeService;
import pixpaymentgateway.webhook.WebhookService.PixNotification;
import pixpaymentgateway.webhook.WebhookService.WebhookPayload;
import pixpaymentgateway.webhook.WebhookService.WebhookResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Simula o PSP pagando uma cobrança: monta o webhook, assina e entrega ao próprio gateway. */
@RestController
@RequestMapping("/v1/sandbox")
@Tag(name = "Sandbox")
@ConditionalOnProperty(name = "pix.sandbox.enabled", havingValue = "true")
public class SandboxController {

    private final ChargeService charges;
    private final WebhookService webhook;
    private final JsonMapper json;
    private final Clock clock;

    public SandboxController(ChargeService charges, WebhookService webhook, JsonMapper json, Clock clock) {
        this.charges = charges;
        this.webhook = webhook;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping("/charges/{txid}/pay")
    @Operation(summary = "Simula o pagamento de uma cobrança (opcionalmente com outro valor, para testar divergências)")
    public WebhookResult pay(@PathVariable String txid, @RequestParam(required = false) BigDecimal amount) {
        var charge = charges.get(txid);
        var notification = new PixNotification(endToEndId(), txid,
                amount != null ? amount : charge.getAmount(), clock.instant());
        var body = json.writeValueAsString(new WebhookPayload(List.of(notification)));
        return webhook.process(body, webhook.sign(body));
    }

    /** Formato do endToEndId: E + ISPB (8) + data/hora (12) + sequencial (11). */
    private String endToEndId() {
        var random = ThreadLocalRandom.current();
        return "E00000000" + "%012d".formatted(clock.millis() % 1_000_000_000_000L)
                + "%011d".formatted(random.nextLong(100_000_000_000L));
    }
}
