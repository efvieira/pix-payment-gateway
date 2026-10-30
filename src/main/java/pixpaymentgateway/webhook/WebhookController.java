package pixpaymentgateway.webhook;

import pixpaymentgateway.webhook.WebhookService.WebhookResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/pix")
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Notificação de Pix recebido, enviada pelo PSP e assinada com HMAC-SHA256 no header X-Pix-Signature")
    public WebhookResult receive(@RequestHeader(value = "X-Pix-Signature", required = false) String signature,
                                 @RequestBody String body) {
        return service.process(body, signature);
    }
}
