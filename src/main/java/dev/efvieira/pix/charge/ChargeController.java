package dev.efvieira.pix.charge;

import dev.efvieira.pix.charge.ChargeDtos.ChargeResponse;
import dev.efvieira.pix.charge.ChargeDtos.CreateChargeRequest;
import dev.efvieira.pix.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/charges")
@Tag(name = "Cobranças")
public class ChargeController {

    private final ChargeService service;
    private final IdempotencyService idempotency;

    public ChargeController(ChargeService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    @Operation(summary = "Cria uma cobrança Pix imediata. O header Idempotency-Key evita cobranças duplicadas em retentativas.")
    public ResponseEntity<?> create(@RequestHeader("Idempotency-Key") String key,
                                    @Valid @RequestBody CreateChargeRequest request) {
        return idempotency.execute(key, request,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(ChargeResponse.from(service.create(request))));
    }

    @GetMapping("/{txid}")
    public ChargeResponse get(@PathVariable String txid) {
        return ChargeResponse.from(service.get(txid));
    }

    @DeleteMapping("/{txid}")
    public ChargeResponse remove(@PathVariable String txid) {
        return ChargeResponse.from(service.remove(txid));
    }
}
