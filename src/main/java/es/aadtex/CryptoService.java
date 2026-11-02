package es.aadtex;

import java.security.GeneralSecurityException;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    public static final String SECRET = "AadTexSecret2026";
    // Modo requerido por los ficheros del ejercicio.
    private byte[] transform(byte[] bytes, int mode) throws GeneralSecurityException {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "AES");
        var cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(mode, key);
        return cipher.doFinal(bytes);
    }
    public byte[] encrypt(byte[] clear) throws GeneralSecurityException { return transform(clear, Cipher.ENCRYPT_MODE); }
    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException { return transform(encrypted, Cipher.DECRYPT_MODE); }
}
