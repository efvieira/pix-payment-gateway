package pixpaymentgateway.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
}
