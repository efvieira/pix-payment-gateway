package pixpaymentgateway.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("pix")
public record PixProperties(
        String merchantName,
        String merchantCity,
        String locationBaseUrl,
        String webhookSecret,
        Duration defaultExpiration,
        Outbox outbox) {

    public record Outbox(String topic, int batchSize, int maxAttempts, Duration baseBackoff) {
    }
}
