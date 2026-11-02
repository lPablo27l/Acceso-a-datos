package es.aadtex;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Identidad reproducible: mismo nombre y contenido, mismo resumen. */
public final class BatchIdentity {
    private BatchIdentity() { }
    public static String of(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0); // Separa el nombre del contenido sin ambigüedad.
            try (var in = new BufferedInputStream(Files.newInputStream(file), 4096)) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
