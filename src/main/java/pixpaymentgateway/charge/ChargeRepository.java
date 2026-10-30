package pixpaymentgateway.charge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    Optional<Charge> findByTxid(String txid);

    List<Charge> findTop100ByStatusAndExpiresAtBefore(ChargeStatus status, Instant instant);

    List<Charge> findByPaidAtBetween(Instant start, Instant end);
}
