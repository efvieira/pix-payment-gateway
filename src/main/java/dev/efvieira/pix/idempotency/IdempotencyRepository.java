package dev.efvieira.pix.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
}
