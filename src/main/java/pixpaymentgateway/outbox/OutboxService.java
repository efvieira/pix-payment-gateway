package pixpaymentgateway.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transactional outbox: o evento é gravado na mesma transação da mudança de estado.
 * Assim, nunca existe cobrança paga sem evento, nem evento de algo que sofreu rollback.
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final JsonMapper json;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, JsonMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, String eventType, Map<String, ?> data) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("type", eventType);
        envelope.put("occurredAt", clock.instant().toString());
        envelope.put("data", data);
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType,
                json.writeValueAsString(envelope), clock.instant()));
    }
}
