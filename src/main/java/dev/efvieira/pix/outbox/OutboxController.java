package dev.efvieira.pix.outbox;

import dev.efvieira.pix.shared.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/outbox")
@Tag(name = "Operação")
public class OutboxController {

    private final OutboxRepository repository;
    private final Clock clock;

    public OutboxController(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public record FailedEvent(UUID id, String type, int attempts, String lastError) {
    }

    @GetMapping("/stats")
    public Map<OutboxStatus, Long> stats() {
        var stats = new EnumMap<OutboxStatus, Long>(OutboxStatus.class);
        for (var status : OutboxStatus.values()) {
            stats.put(status, repository.countByStatus(status));
        }
        return stats;
    }

    @GetMapping("/failed")
    public List<FailedEvent> failed() {
        return repository.findByStatus(OutboxStatus.FAILED).stream()
                .map(e -> new FailedEvent(e.getId(), e.getEventType(), e.getAttempts(), e.getLastError()))
                .toList();
    }

    @PostMapping("/{id}/retry")
    @Transactional
    @Operation(summary = "Reenfileira um evento que esgotou as tentativas")
    public void retry(@PathVariable UUID id) {
        var event = repository.findById(id).orElseThrow(() -> BusinessException.notFound("Evento não encontrado"));
        if (event.getStatus() != OutboxStatus.FAILED) {
            throw BusinessException.unprocessable("Só eventos FAILED podem ser reprocessados");
        }
        event.retry(clock.instant());
    }
}
