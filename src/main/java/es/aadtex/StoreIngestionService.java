package es.aadtex;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StoreIngestionService {
    private static final Logger log=LoggerFactory.getLogger(StoreIngestionService.class);
    private final StoragePaths paths;
    private final CryptoService crypto;
    private final CsvSalesReader csv;
    private final SignatureService signatures;
    private final CloudBucketService bucket;
    public StoreIngestionService(StoragePaths paths, CryptoService crypto, CsvSalesReader csv,
                                 SignatureService signatures, CloudBucketService bucket) {
        this.paths=paths; this.crypto=crypto; this.csv=csv; this.signatures=signatures; this.bucket=bucket;
    }
    public synchronized void processPending() throws IOException {
        paths.initialize();
        List<Path> files;
        try (var stream=Files.list(paths.input())) {
            files=stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparingInt((Path p)->extension(p).equals("jpg")?0:1)
                .thenComparing(p->p.getFileName().toString())).toList();
        }
        log.info("[SCAN] Entrada: {} | ficheros: {}", paths.root().relativize(paths.input()), files.size());
        for (Path file:files) {
            String ext=extension(file);
            if (!Set.of("csv","enc","jpg","zip").contains(ext)) continue;
            try {
                if (Files.size(file)==0) throw new BatchValidationException("Fichero de 0 bytes");
                String batchId = ext.equals("jpg") ? null : BatchIdentity.of(file);
                if (batchId != null && bucket.published(batchId).isPresent()) {
                    archive(file);
                    log.info("[RECOVER] {} ya publicado; archivado sin duplicar JSON", file.getFileName());
                    continue;
                }
                switch (ext) {
                    case "jpg" -> copySignature(file);
                    case "csv", "enc" -> processSales(file,ext,batchId);
                    case "zip" -> processZip(file,batchId);
                    default -> { }
                }
            } catch (BatchValidationException e) {
                log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {} | {}",
                    file.getFileName(),e.getMessage());
                try { StoragePaths.move(file, StoragePaths.uniqueDestination(paths.error(),file.getFileName().toString())); }
                catch (IOException moveError) { log.error("No se pudo aislar {}",file,moveError); }
            } catch (Exception e) {
                // Un problema de disco o publicación no convierte un lote válido en corrupto.
                log.error("[RETRY] {} permanece en input: {}", file.getFileName(), e.getMessage());
            }
        }
    }
    private static String extension(Path file) {
        String n=file.getFileName().toString(); int dot=n.lastIndexOf('.');
        return dot<0?"":n.substring(dot+1).toLowerCase(Locale.ROOT);
    }
    private void copySignature(Path file) throws IOException {
        Path temp=paths.temporary(".jpg.tmp");
        try {
            signatures.copy(Files.newInputStream(file),temp);
            StoragePaths.move(temp, paths.signatures().resolve(file.getFileName()));
            Files.delete(file);
            log.info("[JPG] {} copiada con buffer de 4096 bytes",file.getFileName());
        } finally { Files.deleteIfExists(temp); }
    }
    private void processSales(Path file, String extension, String batchId) throws Exception {
        String name=file.getFileName().toString();
        String stem=name.substring(0,name.lastIndexOf('.'));
        if (!stem.startsWith("daily_sales_")) throw new BatchValidationException("Nombre de lote no reconocido");
        String signature="signature_"+stem.substring("daily_sales_".length())+".jpg";
        if (!Files.isRegularFile(paths.signatures().resolve(signature))) {
            log.warn("[WAIT] {} espera la firma {}",name,signature); return;
        }
        SalesSummary summary;
        if (extension.equals("enc")) {
            // Lectura acotada incluso si el productor aumenta el fichero durante la lectura.
            byte[] encrypted;
            try (var input = Files.newInputStream(file)) {
                encrypted = input.readNBytes(CsvSalesReader.MAX_BYTES + 1);
            }
            if (encrypted.length > CsvSalesReader.MAX_BYTES)
                throw new BatchValidationException("Cifrado superior al límite de 16 MiB");
            byte[] clear;
            try { clear = crypto.decrypt(encrypted); }
            catch (BadPaddingException | IllegalBlockSizeException e) {
                throw new BatchValidationException("Lote AES incompleto o cifrado incorrecto", e);
            }
            try (var in=new ByteArrayInputStream(clear)) { summary=csv.read(in,name); }
            finally { Arrays.fill(clear,(byte)0); }
            log.info("[AES] {} descifrado en RAM; sin CSV financiero en disco",name);
        } else {
            try (var in=Files.newInputStream(file)) { summary=csv.read(in,name); }
        }
        complete(file,summary.identifiedBy(batchId));
    }
    private static InputStream leaveOpen(InputStream in) {
        return new FilterInputStream(in) { @Override public void close() { /* El dueño cierra el ZIP. */ } };
    }
    private void processZip(Path file, String batchId) throws Exception {
        SalesSummary summary=null;
        Path tempSignature=paths.temporary(".jpg.tmp");
        String signatureName=null, csvName=null;
        Set<String> seen=new HashSet<>();
        try {
            // Comprueba también el directorio central; un ZIP truncado no se acepta.
            try (var archive=new ZipFile(file.toFile())) {
                if (archive.size()>20) throw new BatchValidationException("Demasiadas entradas ZIP");
            }
            try (var zip=new ZipInputStream(Files.newInputStream(file))) {
                ZipEntry entry;
                int count=0;
                while ((entry=zip.getNextEntry())!=null) {
                    if (++count>20) throw new BatchValidationException("Demasiadas entradas ZIP");
                    String name=entry.getName().replace('\\','/');
                    if (name.startsWith("/") || name.contains(":") || Arrays.asList(name.split("/")).contains(".."))
                        throw new BatchValidationException("Ruta ZIP peligrosa: "+name);
                    if (entry.isDirectory()) { zip.closeEntry(); continue; }
                    // Sólo nombres simples; no se usan rutas proporcionadas por el ZIP.
                    if (name.contains("/") || !seen.add(name)) throw new BatchValidationException("Entrada ZIP anidada o duplicada");
                    if (name.endsWith(".csv") && summary==null) {
                        csvName=name; summary=csv.read(leaveOpen(zip),file.getFileName()+"!"+name);
                    } else if (name.startsWith("signature_") && name.endsWith(".jpg") && signatureName==null) {
                        signatureName=name; signatures.copy(leaveOpen(zip),tempSignature);
                    } else throw new BatchValidationException("Contenido ZIP inesperado: "+name);
                    zip.closeEntry();
                }
            }
            if (summary==null || signatureName==null || !csvName.startsWith("daily_sales_")
                || !signatureName.equals(csvName.replace("daily_sales_","signature_").replace(".csv",".jpg")))
                throw new BatchValidationException("ZIP sin pareja CSV/firma coherente");
            // Publicar únicamente cuando todas las entradas han superado las validaciones y CRC.
            Path signature=paths.signatures().resolve(signatureName);
            if (Files.exists(signature) && Files.mismatch(tempSignature, signature) == -1) {
                // Recupera un cierre entre la publicación de la firma y la del resumen.
                Files.delete(tempSignature);
            } else StoragePaths.move(tempSignature, signature);
            complete(file, summary.identifiedBy(batchId));
            log.info("[ZIP] {} procesado en flujo con ZipInputStream; sin CSV temporal",file.getFileName());
        } catch (ZipException e) {
            throw new BatchValidationException("ZIP incompleto o CRC incorrecto", e);
        } finally { Files.deleteIfExists(tempSignature); }
    }
    private void archive(Path file) throws IOException {
        StoragePaths.move(file, StoragePaths.uniqueDestination(paths.processed(), file.getFileName().toString()));
    }
    private void complete(Path file, SalesSummary summary) throws IOException {
        if (!summary.batchId().equals(BatchIdentity.of(file)))
            throw new IOException("El archivo cambió durante la lectura; se reintentará");
        Path report = bucket.publish(summary);
        // Si falla el movimiento, conservamos JSON e input. El próximo ciclo reconoce la huella.
        archive(file);
        log.info("[OK] {} | transacciones={} | total={} | tiendas={}", summary.sourceFile(), summary.transactions(), summary.totalSales(), summary.stores());
        log.info("[JSON] {}", paths.root().relativize(report));
    }
}
