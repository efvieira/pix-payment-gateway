package dev.efvieira.pix.reconciliation;

import dev.efvieira.pix.charge.Charge;
import dev.efvieira.pix.charge.ChargeRepository;
import dev.efvieira.pix.shared.Clocks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Concilia o extrato do PSP (o que de fato caiu na conta) com os pagamentos registrados
 * no gateway num dia. Toda divergência vira um item do relatório para tratamento manual.
 */
@Service
public class ReconciliationService {

    public enum DivergenceType { AMOUNT_MISMATCH, MISSING_IN_GATEWAY, MISSING_IN_STATEMENT }

    public record StatementEntry(@NotBlank String endToEndId, String txid, @NotNull BigDecimal amount) {
    }

    public record ReconciliationRequest(@NotNull LocalDate date, @NotNull List<@Valid StatementEntry> entries) {
    }

    public record Divergence(DivergenceType type, String endToEndId, String txid,
                             BigDecimal statementAmount, BigDecimal gatewayAmount) {
    }

    public record ReconciliationReport(LocalDate date, int matched, BigDecimal statementTotal,
                                       BigDecimal gatewayTotal, List<Divergence> divergences) {
    }

    private final ChargeRepository charges;

    public ReconciliationService(ChargeRepository charges) {
        this.charges = charges;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(ReconciliationRequest request) {
        var start = request.date().atStartOfDay(Clocks.BRT).toInstant();
        var end = request.date().plusDays(1).atStartOfDay(Clocks.BRT).toInstant();
        var paidCharges = charges.findByPaidAtBetween(start, end);
        var gatewayTotal = paidCharges.stream().map(Charge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var paid = paidCharges.stream().collect(Collectors.toMap(Charge::getEndToEndId, Function.identity()));

        var divergences = new ArrayList<Divergence>();
        int matched = 0;
        for (var entry : request.entries()) {
            var charge = paid.remove(entry.endToEndId());
            if (charge == null) {
                divergences.add(new Divergence(DivergenceType.MISSING_IN_GATEWAY, entry.endToEndId(),
                        entry.txid(), entry.amount(), null));
            } else if (charge.getAmount().compareTo(entry.amount()) != 0) {
                divergences.add(new Divergence(DivergenceType.AMOUNT_MISMATCH, entry.endToEndId(),
                        charge.getTxid(), entry.amount(), charge.getAmount()));
            } else {
                matched++;
            }
        }
        paid.values().forEach(c -> divergences.add(new Divergence(DivergenceType.MISSING_IN_STATEMENT,
                c.getEndToEndId(), c.getTxid(), null, c.getAmount())));

        var statementTotal = request.entries().stream().map(StatementEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReconciliationReport(request.date(), matched, statementTotal, gatewayTotal, divergences);
    }
}
