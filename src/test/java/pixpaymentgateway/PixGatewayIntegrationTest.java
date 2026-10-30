package pixpaymentgateway;

import pixpaymentgateway.charge.ChargeService;
import pixpaymentgateway.outbox.OutboxPublisher;
import pixpaymentgateway.outbox.OutboxRepository;
import pixpaymentgateway.outbox.OutboxStatus;
import pixpaymentgateway.webhook.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PixGatewayIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired WebhookService webhook;
    @Autowired OutboxPublisher publisher;
    @Autowired OutboxRepository outbox;
    @Autowired ChargeService charges;

    @MockitoBean KafkaTemplate<String, String> kafka;

    @BeforeEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    private JsonNode createCharge(String key, String body) throws Exception {
        var response = mvc.perform(post("/v1/charges").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void sameIdempotencyKeyReplaysTheOriginalResponse() throws Exception {
        var key = UUID.randomUUID().toString();
        var body = "{\"amount\": 99.90, \"description\": \"Assinatura\"}";
        var first = createCharge(key, body);

        mvc.perform(post("/v1/charges").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.txid").value(first.get("txid").asString()));

        mvc.perform(post("/v1/charges").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1.00}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void validatesRequestAndRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/charges").header("Idempotency-Key", "k-invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/charges").contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sandboxPaymentConfirmsChargeAndWebhookReplayIsIdempotent() throws Exception {
        var txid = createCharge(UUID.randomUUID().toString(), "{\"amount\": 10.00}").get("txid").asString();

        mvc.perform(post("/v1/sandbox/charges/{txid}/pay", txid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(1));

        var charge = mvc.perform(get("/v1/charges/{txid}", txid))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn().getResponse().getContentAsString();
        var endToEndId = json.readTree(charge).get("endToEndId").asString();

        var replay = "{\"pix\":[{\"endToEndId\":\"%s\",\"txid\":\"%s\",\"valor\":\"10.00\",\"horario\":\"2026-01-01T00:00:00Z\"}]}"
                .formatted(endToEndId, txid);
        mvc.perform(post("/v1/webhooks/pix").header("X-Pix-Signature", webhook.sign(replay))
                        .contentType(MediaType.APPLICATION_JSON).content(replay))
                .andExpect(jsonPath("$.duplicated").value(1));
    }

    @Test
    void rejectsInvalidSignatureAndUnknownTxid() throws Exception {
        var body = "{\"pix\":[{\"endToEndId\":\"E1\",\"txid\":\"nao-existe\",\"valor\":\"1.00\",\"horario\":\"2026-01-01T00:00:00Z\"}]}";
        mvc.perform(post("/v1/webhooks/pix").header("X-Pix-Signature", "invalida")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/webhooks/pix").header("X-Pix-Signature", webhook.sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.unknown").value(1));
    }

    @Test
    void paymentWithDifferentAmountIsRejected() throws Exception {
        var txid = createCharge(UUID.randomUUID().toString(), "{\"amount\": 50.00}").get("txid").asString();
        mvc.perform(post("/v1/sandbox/charges/{txid}/pay", txid).param("amount", "49.00"))
                .andExpect(jsonPath("$.rejected").value(1));
    }

    @Test
    void removesActiveChargeOnlyOnce() throws Exception {
        var txid = createCharge(UUID.randomUUID().toString(), "{\"amount\": 5.00}").get("txid").asString();
        mvc.perform(delete("/v1/charges/{txid}", txid)).andExpect(jsonPath("$.status").value("REMOVED"));
        mvc.perform(delete("/v1/charges/{txid}", txid)).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/v1/charges/{txid}", "inexistente")).andExpect(status().isNotFound());
    }

    @Test
    void reconciliationReportsMatchesAndDivergences() throws Exception {
        var txid = createCharge(UUID.randomUUID().toString(), "{\"amount\": 30.00}").get("txid").asString();
        mvc.perform(post("/v1/sandbox/charges/{txid}/pay", txid));
        var paid = json.readTree(mvc.perform(get("/v1/charges/{txid}", txid)).andReturn().getResponse().getContentAsString());
        var paidDate = LocalDate.ofInstant(java.time.Instant.parse(paid.get("paidAt").asString()), java.time.ZoneId.of("America/Sao_Paulo"));

        var request = """
                {"date": "%s", "entries": [
                  {"endToEndId": "%s", "txid": "%s", "amount": 30.00},
                  {"endToEndId": "E-SO-NO-EXTRATO", "amount": 12.00}
                ]}""".formatted(paidDate, paid.get("endToEndId").asString(), txid);

        mvc.perform(post("/v1/reconciliation").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(1))
                .andExpect(jsonPath("$.divergences[?(@.type == 'MISSING_IN_GATEWAY')]").exists());
    }

    @Test
    void outboxPublishesPendingEventsAndBacksOffOnFailure() throws Exception {
        createCharge(UUID.randomUUID().toString(), "{\"amount\": 7.00}");

        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        assertThat(publisher.publishPending()).isZero();
        assertThat(outbox.findAll()).allSatisfy(e -> assertThat(e.getAttempts()).isEqualTo(1));

        mvc.perform(get("/v1/admin/outbox/stats")).andExpect(jsonPath("$.PENDING").value(1));
        mvc.perform(get("/v1/admin/outbox/failed")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void outboxMarksEventsAsPublished() throws Exception {
        createCharge(UUID.randomUUID().toString(), "{\"amount\": 8.00}");
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(publisher.publishPending()).isEqualTo(1);
        assertThat(outbox.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(1);
        mvc.perform(post("/v1/admin/outbox/{id}/retry", outbox.findAll().getFirst().getId()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void expirationJobRunsWithoutDueCharges() {
        assertThat(charges.expireDueCharges()).isGreaterThanOrEqualTo(0);
    }
}
