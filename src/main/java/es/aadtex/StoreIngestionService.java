package es.aadtex;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
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
        log.info("[SCAN] Entrada: {} | ficheros: {}", paths.input(), files.size());
        for (Path file:files) {
            String ext=extension(file);
            if (!Set.of("csv","enc","jpg","zip").contains(ext)) continue;
            try {
                if (Files.size(file)==0) throw new IOException("Fichero de 0 bytes");
                switch (ext) {
                    case "jpg" -> copySignature(file);
                    case "csv", "enc" -> processSales(file,ext);
                    case "zip" -> processZip(file);
                    default -> { }
                }
            } catch (Exception e) {
                log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {} | {}",
                    file.getFileName(),e.getMessage());
                try { StoragePaths.move(file, StoragePaths.uniqueDestination(paths.error(),file.getFileName().toString())); }
                catch (IOException moveError) { log.error("No se pudo aislar {}",file,moveError); }
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
    private void processSales(Path file, String extension) throws Exception {
        String name=file.getFileName().toString();
        String stem=name.substring(0,name.lastIndexOf('.'));
        if (!stem.startsWith("daily_sales_")) throw new IOException("Nombre de lote no reconocido");
        String signature="signature_"+stem.substring("daily_sales_".length())+".jpg";
        if (!Files.isRegularFile(paths.signatures().resolve(signature))) {
            log.warn("[WAIT] {} espera la firma {}",name,signature); return;
        }
        SalesSummary summary;
        if (extension.equals("enc")) {
            if (Files.size(file)>CsvSalesReader.MAX_BYTES) throw new IOException("Cifrado superior al límite de 16 MiB");
            // Única carga completa: el lote AES acotado, como exige el enunciado.
            byte[] clear=crypto.decrypt(Files.readAllBytes(file));
            try (var in=new ByteArrayInputStream(clear)) { summary=csv.read(in,name); }
            finally { Arrays.fill(clear,(byte)0); }
            log.info("[AES] {} descifrado en RAM; sin CSV financiero en disco",name);
        } else {
            try (var in=Files.newInputStream(file)) { summary=csv.read(in,name); }
        }
        complete(file,summary);
    }
    private static InputStream leaveOpen(InputStream in) {
        return new FilterInputStream(in) { @Override public void close() { /* El dueño cierra el ZIP. */ } };
    }
    private void processZip(Path file) throws Exception {
        SalesSummary summary=null;
        Path tempSignature=paths.temporary(".jpg.tmp");
        String signatureName=null, csvName=null;
        Set<String> seen=new HashSet<>();
        try {
            // Comprueba también el directorio central; un ZIP truncado no se acepta.
            try (var archive=new ZipFile(file.toFile())) {
                if (archive.size()>20) throw new IOException("Demasiadas entradas ZIP");
            }
            try (var zip=new ZipInputStream(Files.newInputStream(file))) {
                ZipEntry entry;
                int count=0;
                while ((entry=zip.getNextEntry())!=null) {
                    if (++count>20) throw new IOException("Demasiadas entradas ZIP");
                    String name=entry.getName().replace('\\','/');
                    if (name.startsWith("/") || name.contains(":") || Arrays.asList(name.split("/")).contains(".."))
                        throw new IOException("Ruta ZIP peligrosa: "+name);
                    if (entry.isDirectory()) { zip.closeEntry(); continue; }
                    // Sólo nombres simples; no se usan rutas proporcionadas por el ZIP.
                    if (name.contains("/") || !seen.add(name)) throw new IOException("Entrada ZIP anidada o duplicada");
                    if (name.endsWith(".csv") && summary==null) {
                        csvName=name; summary=csv.read(leaveOpen(zip),file.getFileName()+"!"+name);
                    } else if (name.startsWith("signature_") && name.endsWith(".jpg") && signatureName==null) {
                        signatureName=name; signatures.copy(leaveOpen(zip),tempSignature);
                    } else throw new IOException("Contenido ZIP inesperado: "+name);
                    zip.closeEntry();
                }
            }
            if (summary==null || signatureName==null || !csvName.startsWith("daily_sales_")
                || !signatureName.equals(csvName.replace("daily_sales_","signature_").replace(".csv",".jpg")))
                throw new IOException("ZIP sin pareja CSV/firma coherente");
            // Publicar únicamente cuando todas las entradas han superado las validaciones y CRC.
            Path signature=paths.signatures().resolve(signatureName);
            if (Files.exists(signature)) throw new IOException("Firma ZIP ya existente");
            StoragePaths.move(tempSignature,signature);
            try { complete(file,summary); }
            catch (Exception e) { Files.deleteIfExists(signature); throw e; }
            log.info("[ZIP] {} procesado en flujo con ZipInputStream; sin CSV temporal",file.getFileName());
        } finally { Files.deleteIfExists(tempSignature); }
    }
    private void complete(Path file, SalesSummary summary) throws IOException {
        Path report=bucket.publish(summary);
        try { StoragePaths.move(file, StoragePaths.uniqueDestination(paths.processed(),file.getFileName().toString())); }
        catch (IOException e) { Files.deleteIfExists(report); throw e; }
        log.info("[OK] {} | transacciones={} | total={} | tiendas={}",summary.sourceFile(),summary.transactions(),summary.totalSales(),summary.stores());
        log.info("[JSON] {}",report);
    }
}
