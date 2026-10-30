package pixpaymentgateway.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(OutboxStatus status, Instant now, Limit limit);

    List<OutboxEvent> findByStatus(OutboxStatus status);

    long countByStatus(OutboxStatus status);
}
