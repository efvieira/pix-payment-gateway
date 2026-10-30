package dev.efvieira.pix.idempotency;

import dev.efvieira.pix.shared.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Garante que a mesma requisição, repetida com a mesma Idempotency-Key, produza o mesmo
 * efeito uma única vez. A resposta original é gravada na mesma transação da operação e
 * devolvida nas retentativas. Mesma chave com corpo diferente é rejeitada (422).
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final JsonMapper json;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, JsonMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ResponseEntity<?> execute(String key, Object request, Supplier<ResponseEntity<?>> operation) {
        if (key.isBlank() || key.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Idempotency-Key deve ter entre 1 e 64 caracteres");
        }
        var hash = sha256(json.writeValueAsString(request));
        var existing = repository.findById(key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.getRequestHash().equals(hash)) {
                throw BusinessException.unprocessable("Idempotency-Key já usada com outro corpo de requisição");
            }
            return ResponseEntity.status(record.getResponseStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotent-Replayed", "true")
                    .body(record.getResponseBody());
        }
        var response = operation.get();
        repository.save(new IdempotencyRecord(key, hash, response.getStatusCode().value(),
                json.writeValueAsString(response.getBody()), clock.instant()));
        return response;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
