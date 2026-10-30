package dev.efvieira.pix.reconciliation;

import dev.efvieira.pix.reconciliation.ReconciliationService.ReconciliationReport;
import dev.efvieira.pix.reconciliation.ReconciliationService.ReconciliationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reconciliation")
@Tag(name = "Conciliação")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Concilia o extrato do PSP de um dia com os pagamentos registrados no gateway")
    public ReconciliationReport reconcile(@Valid @RequestBody ReconciliationRequest request) {
        return service.reconcile(request);
    }
}
