package co.kartrent.infraestructura.pagos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Valida la firma HMAC-SHA256 que la pasarela envía en el webhook (HU-01). */
@Component
public class VerificadorFirmaWebhook {
    private final byte[] secreto;

    public VerificadorFirmaWebhook(@Value("${kartrent.webhook-secreto}") String secreto) {
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    public String firmar(String cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean esValida(String cuerpo, String firma) {
        if (firma == null) return false;
        return MessageDigest.isEqual(firmar(cuerpo).getBytes(StandardCharsets.UTF_8),
                firma.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
