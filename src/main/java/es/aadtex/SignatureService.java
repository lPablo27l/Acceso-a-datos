package es.aadtex;

import java.io.*;
import java.nio.file.*;
import org.springframework.stereotype.Service;

@Service
public class SignatureService {
    // Copia en flujo: nunca carga la imagen completa en RAM.
    public void copy(InputStream source, Path destination) throws IOException {
        try (var input = new BufferedInputStream(source, 4096);
             var output = new BufferedOutputStream(Files.newOutputStream(destination), 4096)) {
            byte[] buffer = new byte[4096];
            int n = input.readNBytes(buffer, 0, 3);
            if (n != 3 || (buffer[0]&255)!=255 || (buffer[1]&255)!=216 || (buffer[2]&255)!=255)
                throw new IOException("Firma sin cabecera JPEG válida");
            output.write(buffer,0,n);
            int previous=buffer[1]&255, last=buffer[2]&255;
            while ((n=input.read(buffer)) != -1) {
                if (n>0) {
                    previous = n>=2 ? buffer[n-2]&255 : last;
                    last=buffer[n-1]&255; output.write(buffer,0,n);
                }
            }
            if (previous!=255 || last!=217) throw new IOException("Firma JPEG truncada");
        }
    }
}
