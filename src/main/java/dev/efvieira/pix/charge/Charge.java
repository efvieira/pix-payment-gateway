package dev.efvieira.pix.charge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "charges")
public class Charge {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 35)
    private String txid;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private String description;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_document", length = 14)
    private String payerDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChargeStatus status;

    @Column(name = "br_code", nullable = false, length = 512)
    private String brCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "end_to_end_id", unique = true, length = 32)
    private String endToEndId;

    @Version
    private long version;

    protected Charge() {
    }

    Charge(String txid, BigDecimal amount, String description, String payerName, String payerDocument,
           String brCode, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.txid = txid;
        this.amount = amount;
        this.description = description;
        this.payerName = payerName;
        this.payerDocument = payerDocument;
        this.brCode = brCode;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = ChargeStatus.ACTIVE;
    }

    public PaymentOutcome pay(String endToEndId, BigDecimal paidAmount, Instant when) {
        if (status == ChargeStatus.PAID) {
            return endToEndId.equals(this.endToEndId)
                    ? new PaymentOutcome.Duplicate(this)
                    : new PaymentOutcome.Rejected(this, "Cobrança já paga por outro endToEndId");
        }
        if (status != ChargeStatus.ACTIVE) {
            return new PaymentOutcome.Rejected(this, "Cobrança não está ativa: " + status);
        }
        if (when.isAfter(expiresAt)) {
            return new PaymentOutcome.Rejected(this, "Pagamento após a expiração");
        }
        if (paidAmount.compareTo(amount) != 0) {
            return new PaymentOutcome.Rejected(this, "Valor pago %s difere do cobrado %s".formatted(paidAmount, amount));
        }
        this.status = ChargeStatus.PAID;
        this.endToEndId = endToEndId;
        this.paidAt = when;
        return new PaymentOutcome.Confirmed(this);
    }

    public boolean expireIfDue(Instant now) {
        if (status == ChargeStatus.ACTIVE && now.isAfter(expiresAt)) {
            status = ChargeStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public void remove() {
        if (status != ChargeStatus.ACTIVE) {
            throw new IllegalStateException("Só cobranças ativas podem ser removidas");
        }
        status = ChargeStatus.REMOVED;
    }

    public UUID getId() { return id; }
    public String getTxid() { return txid; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getPayerName() { return payerName; }
    public String getPayerDocument() { return payerDocument; }
    public ChargeStatus getStatus() { return status; }
    public String getBrCode() { return brCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public String getEndToEndId() { return endToEndId; }
}
