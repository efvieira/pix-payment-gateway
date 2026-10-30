package dev.efvieira.pix.brcode;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrCodeTest {

    @Test
    void crc16UsesCcittFalseCheckValue() {
        assertThat(BrCode.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void dynamicPayloadFollowsEmvLayoutAndEndsWithValidCrc() {
        var code = BrCode.dynamic("pix.example.com/qr/v2/abc", new BigDecimal("10.5"), "Padaria São João", "São Paulo");

        assertThat(code)
                .startsWith("000201010212")
                .contains("0014br.gov.bcb.pix")
                .contains("2525pix.example.com/qr/v2/abc")
                .contains("540510.50")
                .contains("5916PADARIA SAO JOAO")
                .contains("6009SAO PAULO")
                .contains("62070503***");
        var body = code.substring(0, code.length() - 4);
        assertThat(code.substring(code.length() - 4)).isEqualTo(BrCode.crc16(body));
    }

    @Test
    void truncatesMerchantNameTo25Characters() {
        var code = BrCode.dynamic("x", BigDecimal.ONE, "Nome de loja muito comprido demais", "Cidade");
        assertThat(code).contains("5925NOME DE LOJA MUITO COMPRI");
    }

    @Test
    void rejectsFieldsLongerThan99Characters() {
        assertThatThrownBy(() -> BrCode.field("25", "x".repeat(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
