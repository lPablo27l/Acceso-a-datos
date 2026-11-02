package es.aadtex;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StoreIngestionService {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionService.class);
    private final Path input;
    private final Path processed;
    private final Path signatures;
    private final Path error;
    private final CsvSalesReader csv;
    private final CryptoService crypto;
    private final CloudBucketService bucket;

    public StoreIngestionService(@Value("${aadtex.data-dir}") String root,
            CsvSalesReader csv, CryptoService crypto, CloudBucketService bucket) {
        input = Path.of(root, "input");
        processed = Path.of(root, "output", "processed");
        signatures = processed.resolve("signatures");
        error = Path.of(root, "output", "error");
        this.csv = csv;
        this.crypto = crypto;
        this.bucket = bucket;
    }

    public void processPending() {
        try {
            for (Path directory : List.of(input, processed, signatures, error)) {
                if (!Files.exists(directory)) Files.createDirectories(directory);
            }
            List<Path> files;
            try (var listing = Files.list(input)) {
                // Primero las firmas sueltas; después sus correspondientes ventas.
                files = listing.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingInt((Path p) -> extension(p).equals("jpg") ? 0 : 1)
                    .thenComparing(p -> p.getFileName().toString())).toList();
            }
            log.info("[SCAN] Archivos en input: {}", files.size());
            for (Path file : files) processFile(file);
        } catch (IOException e) {
            log.error("No se pueden preparar o leer los directorios: {}", e.getMessage());
        }
    }

    private void processFile(Path file) {
        String ext = extension(file);
        if (!List.of("csv", "enc", "jpg", "zip").contains(ext)) return;
        try {
            if (Files.size(file) == 0) throw new IOException("Archivo de 0 bytes");
            if (ext.equals("jpg")) {
                try (InputStream source = Files.newInputStream(file)) {
                    copySignature(source, signatures.resolve(file.getFileName()));
                }
                Files.delete(file);
                log.info("[JPG] {} copiada con buffer de 4096 bytes", file.getFileName());
                return;
            }
            SalesSummary summary;
            if (ext.equals("zip")) {
                summary = processZip(file);
                log.info("[ZIP] {} leído con ZipInputStream, sin archivos temporales", file.getFileName());
            } else {
                String name = file.getFileName().toString();
                String signatureName = name.replace("daily_sales_", "signature_")
                        .replace(".csv", ".jpg").replace(".enc", ".jpg");
                if (!Files.exists(signatures.resolve(signatureName))) {
                    log.warn("[ESPERA] {} espera su firma", name);
                    return;
                }
                if (ext.equals("enc")) {
                    byte[] clear = crypto.decrypt(Files.readAllBytes(file));
                    try (InputStream source = new ByteArrayInputStream(clear)) {
                        summary = csv.read(source, name);
                    } finally {
                        Arrays.fill(clear, (byte) 0);
                    }
                    log.info("[AES] {} descifrado en memoria RAM", name);
                } else {
                    try (InputStream source = Files.newInputStream(file)) {
                        summary = csv.read(source, name);
                    }
                }
            }
            bucket.publish(summary);
            move(file, processed.resolve(file.getFileName()));
            log.info("[OK] {} | ventas={} | total={} | tiendas={}",
                    summary.sourceFile(), summary.transactions(), summary.totalSales(), summary.stores());
        } catch (Exception e) {
            log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {} | {}",
                    file.getFileName(), e.getMessage());
            try {
                move(file, error.resolve(file.getFileName()));
            } catch (IOException moveError) {
                log.error("No se pudo mover el archivo a error: {}", moveError.getMessage());
            }
        }
    }

    private SalesSummary processZip(Path file) throws IOException {
        // ZipFile comprueba el directorio central; ZipInputStream procesa el contenido.
        try (ZipFile check = new ZipFile(file.toFile())) {
            if (check.size() == 0) throw new IOException("ZIP vacío");
        }
        SalesSummary summary = null;
        String csvName = null;
        Path signature = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("..")) {
                    throw new IOException("Nombre de archivo ZIP no permitido");
                }
                if (name.endsWith(".csv") && summary == null) {
                    csvName = name;
                    summary = csv.read(leaveOpen(zip), name);
                } else if (name.startsWith("signature_") && name.endsWith(".jpg") && signature == null) {
                    signature = signatures.resolve(name);
                    // Se escribe directamente en su destino con buffer de 4 KB.
                    copySignature(leaveOpen(zip), signature);
                } else {
                    throw new IOException("El ZIP debe contener un CSV y su firma JPG");
                }
                zip.closeEntry();
            }
            if (summary == null || signature == null || !csvName.startsWith("daily_sales_")
                    || !signature.getFileName().toString().equals(csvName.replace("daily_sales_", "signature_").replace(".csv", ".jpg"))) {
                throw new IOException("Pareja CSV y firma incompleta en el ZIP");
            }
            return summary;
        } catch (IOException e) {
            if (signature != null) Files.deleteIfExists(signature);
            throw e;
        }
    }

    private void copySignature(InputStream source, Path destination) throws IOException {
        boolean complete = false;
        try (BufferedInputStream in = new BufferedInputStream(source);
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[4096];
            int count = in.readNBytes(buffer, 0, 3);
            if (count != 3 || (buffer[0] & 255) != 255 || (buffer[1] & 255) != 216 || (buffer[2] & 255) != 255) {
                throw new IOException("Cabecera JPEG incorrecta");
            }
            out.write(buffer, 0, count);
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(destination);
        }
    }

    private static InputStream leaveOpen(InputStream source) {
        return new FilterInputStream(source) {
            @Override public void close() { /* El propietario cierra el ZIP completo. */ }
        };
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
