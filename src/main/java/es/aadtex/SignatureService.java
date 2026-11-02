package es.aadtex;

import java.io.*;
import java.nio.file.*;
import org.springframework.stereotype.Service;

@Service
public class SignatureService {
    public static final long MAX_BYTES = 32L * 1024 * 1024;
    // Copia en flujo: nunca carga la imagen completa en RAM.
    public void copy(InputStream source, Path destination) throws IOException {
        try (var input = new BufferedInputStream(source, 4096);
             var output = new BufferedOutputStream(Files.newOutputStream(destination), 4096)) {
            byte[] buffer = new byte[4096];
            int n = input.readNBytes(buffer, 0, 3);
            if (n != 3 || (buffer[0]&255)!=255 || (buffer[1]&255)!=216 || (buffer[2]&255)!=255)
                throw new BatchValidationException("Firma sin cabecera JPEG válida");
            output.write(buffer,0,n);
            int previous=buffer[1]&255, last=buffer[2]&255;
            long copied = n;
            while ((n=input.read(buffer)) != -1) {
                if (n>0) {
                    copied += n;
                    if (copied > MAX_BYTES) throw new BatchValidationException("Firma superior al límite de 32 MiB");
                    previous = n>=2 ? buffer[n-2]&255 : last;
                    last=buffer[n-1]&255; output.write(buffer,0,n);
                }
            }
            if (previous!=255 || last!=217) throw new BatchValidationException("Firma JPEG truncada");
        }
    }
}
