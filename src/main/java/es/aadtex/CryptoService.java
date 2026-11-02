package es.aadtex;

import java.security.GeneralSecurityException;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        byte[] key = "AadTexSecret2026".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(encrypted);
    }
}
