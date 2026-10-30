package pixpaymentgateway.brcode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Gera o "Pix Copia e Cola" (BR Code) no padrão EMV-MPM definido no Manual de Padrões
 * para Iniciação do Pix do Banco Central. Para cobranças dinâmicas o campo 26.25 carrega
 * a URL do payload (location) e o txid vai como "***".
 */
public final class BrCode {

    private static final String GUI = "br.gov.bcb.pix";

    private BrCode() {
    }

    public static String dynamic(String location, BigDecimal amount, String merchantName, String merchantCity) {
        var merchantAccount = field("00", GUI) + field("25", location);
        var payload = field("00", "01")
                + field("01", "12")
                + field("26", merchantAccount)
                + field("52", "0000")
                + field("53", "986")
                + field("54", amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                + field("58", "BR")
                + field("59", sanitize(merchantName, 25))
                + field("60", sanitize(merchantCity, 15))
                + field("62", field("05", "***"))
                + "6304";
        return payload + crc16(payload);
    }

    static String field(String id, String value) {
        if (value.length() > 99) {
            throw new IllegalArgumentException("Campo %s excede 99 caracteres".formatted(id));
        }
        return id + "%02d".formatted(value.length()) + value;
    }

    /** CRC-16/CCITT-FALSE (polinômio 0x1021, valor inicial 0xFFFF), como exige a especificação. */
    public static String crc16(String payload) {
        int crc = 0xFFFF;
        for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
        }
        return "%04X".formatted(crc & 0xFFFF);
    }

    private static String sanitize(String value, int maxLength) {
        var ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]", "").toUpperCase();
        return ascii.length() > maxLength ? ascii.substring(0, maxLength) : ascii;
    }
}
